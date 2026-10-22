package com.dongqh.tradeservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.PayClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.AddressDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.hmapi.dto.OrderCreatedMessage;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.tradeservice.domain.dto.OrderFormDTO;
import com.dongqh.tradeservice.domain.po.Order;
import com.dongqh.tradeservice.domain.po.OrderDetail;
import com.dongqh.tradeservice.domain.po.OrderLogistics;
import com.dongqh.tradeservice.domain.vo.OrderDeliveryVO;
import com.dongqh.tradeservice.domain.vo.OrderVO;
import com.dongqh.tradeservice.domain.vo.ShippingAddressSnapshotVO;
import com.dongqh.tradeservice.mapper.OrderMapper;
import com.dongqh.tradeservice.marketing.CouponCheckoutClient;
import com.dongqh.tradeservice.marketing.CouponReservationView;
import com.dongqh.tradeservice.marketing.MembershipBenefitClient;
import com.dongqh.tradeservice.marketing.MembershipBenefitView;
import com.dongqh.tradeservice.messaging.TradeOutboxService;
import com.dongqh.tradeservice.service.IOrderDetailService;
import com.dongqh.tradeservice.service.IOrderLogisticsService;
import com.dongqh.tradeservice.service.IOrderService;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单服务实现类。
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final int FULL_PRICE_BASIS_POINTS = 10_000;

    private final ItemClient itemClient;
    private final PayClient payClient;
    private final IOrderDetailService detailService;
    private final TradeOutboxService tradeOutboxService;
    private final UserClient userClient;
    private final IOrderLogisticsService logisticsService;
    private final MembershipBenefitClient membershipBenefitClient;
    private final CouponCheckoutClient couponCheckoutClient;


    public OrderServiceImpl(ItemClient itemClient,
                            PayClient payClient,
                            IOrderDetailService detailService,
                            TradeOutboxService tradeOutboxService,
                            UserClient userClient,
                            IOrderLogisticsService logisticsService,
                            MembershipBenefitClient membershipBenefitClient,
                            CouponCheckoutClient couponCheckoutClient) {
        this.itemClient = itemClient;
        this.payClient = payClient;
        this.detailService = detailService;
        this.tradeOutboxService = tradeOutboxService;
        this.userClient = userClient;
        this.logisticsService = logisticsService;
        this.membershipBenefitClient = membershipBenefitClient;
        this.couponCheckoutClient = couponCheckoutClient;
    }

    @Override
    @GlobalTransactional(name = "trade-create-order")
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        Order order = new Order();
        Long userId = UserContext.getUser();



        AddressDTO address = orderFormDTO.getAddressId() == null
                ? null
                : userClient.findAddressById(orderFormDTO.getAddressId());

        if (address == null
                || userId == null
                || !userId.equals(address.getUserId())) {
            throw new BadRequestException("收货地址不存在或不属于当前用户");
        }

        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }

        int originalFee = 0;
        for (ItemDTO item : items) {
            originalFee += item.getPrice() * itemNumMap.get(item.getId());
        }
        MembershipBenefitView membership = membershipBenefitClient.getActiveBenefit();
        int discountBasisPoints = membership.getDiscountBasisPoints();
        int membershipPayableFee = calculatePayable(originalFee, discountBasisPoints);

        order.setId(IdWorker.getId(order));
        CouponReservationView coupon = null;
        try {
            if (orderFormDTO.getCouponId() != null) {
                coupon = couponCheckoutClient.reserve(
                        orderFormDTO.getCouponId(), order.getId(), detailDTOS);
                if (coupon.getDiscountCent() == null || coupon.getDiscountCent() <= 0
                        || coupon.getDiscountCent() > membershipPayableFee) {
                    throw new BadRequestException("优惠券优惠金额无效");
                }
            }
            int couponDiscountFee = coupon == null ? 0 : coupon.getDiscountCent();

            order.setOriginalFee(originalFee);
            order.setMembershipDiscountFee(originalFee - membershipPayableFee);
            order.setMembershipLevel(membership.getMembershipLevel());
            order.setMembershipDiscountBasisPoints(discountBasisPoints);
            order.setCouponId(coupon == null ? null : coupon.getUserCouponId());
            order.setCouponTemplateId(coupon == null ? null : coupon.getTemplateId());
            order.setCouponName(coupon == null ? null : coupon.getCouponName());
            order.setCouponDiscountFee(couponDiscountFee);
            order.setTotalFee(membershipPayableFee - couponDiscountFee);
            order.setPaymentType(orderFormDTO.getPaymentType());
            order.setUserId(userId);
            order.setStatus(1);
            save(order);
            logisticsService.save(buildLogistics(order.getId(), address));

            List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
            detailService.saveBatch(details);
            try {
                itemClient.deductStock(detailDTOS);
            } catch (Exception e) {
                throw new RuntimeException("库存不足！");
            }
            List<Long> orderedItemIds = itemIds.stream().sorted().toList();
            tradeOutboxService.append(new OrderCreatedMessage(
                    UUID.randomUUID(), order.getId(), userId, orderedItemIds, LocalDateTime.now()));
            return order.getId();
        } catch (RuntimeException exception) {
            if (coupon != null && !RootContext.inGlobalTransaction()) {
                try {
                    couponCheckoutClient.release(coupon.getUserCouponId(), order.getId());
                } catch (RuntimeException releaseFailure) {
                    exception.addSuppressed(releaseFailure);
                }
            }
            throw exception;
        }
    }

    @Override
    public OrderPaymentDTO queryOrderForPayment(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            throw new BadRequestException("订单支付查询参数不合法");
        }
        Order order = getById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BadRequestException("订单不存在");
        }
        return BeanUtils.copyBean(order, OrderPaymentDTO.class);
    }

    @Override
    @Transactional
    public void markOrderPaySuccess(Long orderId, Long userId, Integer amount) {
        markOrderPaySuccess(orderId, userId, amount, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void markOrderPaySuccess(Long orderId, Long userId, Integer amount,
                                    LocalDateTime paidAt) {
        if (orderId == null || userId == null || amount == null || amount <= 0 || paidAt == null) {
            throw new BadRequestException("订单支付参数不合法");
        }
        Order order = baseMapper.selectByIdForUpdate(orderId);
        if (order == null || !userId.equals(order.getUserId()) || !amount.equals(order.getTotalFee())) {
            throw new BadRequestException("订单状态或金额已发生变化");
        }
        if (Integer.valueOf(2).equals(order.getStatus())) {
            return;
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            throw new BadRequestException("订单状态或金额已发生变化");
        }
        order.setStatus(2);
        order.setPayTime(paidAt);
        if (baseMapper.updateById(order) != 1) {
            throw new BadRequestException("订单状态或金额已发生变化");
        }
        tradeOutboxService.append(new OrderPaidMessage(UUID.randomUUID(), orderId, userId, paidAt));
    }

    @Override
    public Order lockOrder(Long orderId) {
        return baseMapper.selectByIdForUpdate(orderId);
    }

    @Override
    @GlobalTransactional(name = "trade-apply-checkout-coupon")
    @Transactional
    public OrderVO applyCheckoutCoupon(Long orderId, Long couponId) {
        Order order = requireOwnedOrder(lockOrder(orderId));
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            throw new BadRequestException("只有待付款订单可以补选优惠券");
        }
        if (couponId == null || couponId <= 0) {
            throw new BadRequestException("优惠券ID无效");
        }
        if (order.getCouponId() != null) {
            if (couponId.equals(order.getCouponId())) return BeanUtils.copyBean(order, OrderVO.class);
            throw new BadRequestException("订单已锁定优惠券，不支持换券");
        }
        List<OrderDetailDTO> checkoutItems = validateCheckoutSnapshot(order);
        // Register BEFORE reserve: timeout may mean the remote side has already locked the coupon.
        // User identity is carried by the authenticated AuthorizationContext; orderId is sent explicitly.
        CouponRollbackRelease release = RootContext.inGlobalTransaction()
                ? null
                : new CouponRollbackRelease(couponId, orderId);
        if (release != null
                && org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(release);
        }
        try {
            CouponReservationView coupon = couponCheckoutClient.reserve(couponId, orderId, checkoutItems);
            if (coupon == null || !couponId.equals(coupon.getUserCouponId())
                    || coupon.getDiscountCent() == null || coupon.getDiscountCent() <= 0
                    || coupon.getDiscountCent() >= order.getTotalFee()) {
                // Existing balance gateway requires a positive amount; do not create an unpayable zero-total order.
                throw new BadRequestException("优惠券优惠金额无效或应付金额为零");
            }
            order.setCouponId(couponId);
            order.setCouponTemplateId(coupon.getTemplateId());
            order.setCouponName(coupon.getCouponName());
            order.setCouponDiscountFee(coupon.getDiscountCent());
            order.setTotalFee(Math.subtractExact(order.getTotalFee(), coupon.getDiscountCent()));
            if (!updateById(order)) throw new BadRequestException("订单更新失败");
            payClient.syncPendingAmount(orderId, order.getUserId(), order.getTotalFee());
            return BeanUtils.copyBean(order, OrderVO.class);
        } catch (RuntimeException | Error failure) {
            if (release != null) {
                try { release.release(); } catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
            }
            throw failure;
        }
    }

    @Override
    public List<OrderDetailDTO> queryCheckoutItems(Long orderId) {
        requireOwnedOrder(getById(orderId));
        return checkoutDetails(orderId).stream().map(detail -> new OrderDetailDTO()
                .setItemId(detail.getItemId()).setNum(detail.getNum())).collect(Collectors.toList());
    }

    private List<OrderDetailDTO> validateCheckoutSnapshot(Order order) {
        List<OrderDetail> details = checkoutDetails(order.getId());
        Set<Long> ids = details.stream().map(OrderDetail::getItemId).collect(Collectors.toSet());
        List<ItemDTO> catalog = itemClient.queryItemByIds(ids);
        if (catalog == null || catalog.size() != ids.size()) throw new BadRequestException("订单商品已变更");
        Map<Long, ItemDTO> byId = catalog.stream().collect(Collectors.toMap(ItemDTO::getId, item -> item));
        long gross = 0;
        List<OrderDetailDTO> result = new ArrayList<>();
        for (OrderDetail detail : details) {
            ItemDTO item = byId.get(detail.getItemId());
            if (detail.getItemId() == null || detail.getNum() == null || detail.getNum() <= 0
                    || detail.getPrice() == null || detail.getPrice() <= 0 || item == null
                    || !detail.getPrice().equals(item.getPrice()) || !Integer.valueOf(1).equals(item.getStatus())) {
                throw new BadRequestException("商品价格或状态已变更，不能补选优惠券");
            }
            gross = Math.addExact(gross, Math.multiplyExact((long) detail.getPrice(), detail.getNum()));
            result.add(new OrderDetailDTO().setItemId(detail.getItemId()).setNum(detail.getNum()));
        }
        MembershipBenefitView active = membershipBenefitClient.getActiveBenefit();
        Integer basis = order.getMembershipDiscountBasisPoints();
        int membershipDiscount = order.getMembershipDiscountFee() == null ? 0 : order.getMembershipDiscountFee();
        if (basis == null) basis = FULL_PRICE_BASIS_POINTS; // legacy orders without membership snapshots
        if (active == null || active.getDiscountBasisPoints() != basis
                || order.getTotalFee() == null || order.getTotalFee() <= 0
                || (order.getCouponDiscountFee() != null && order.getCouponDiscountFee() != 0)
                || (order.getOriginalFee() != null && gross != order.getOriginalFee())
                || gross - membershipDiscount != order.getTotalFee()
                || calculatePayable(Math.toIntExact(gross), basis) != order.getTotalFee()) {
            throw new BadRequestException("订单计价快照或会员权益已变更，不能补选优惠券");
        }
        return result;
    }

    private class CouponRollbackRelease implements org.springframework.transaction.support.TransactionSynchronization {
        private final Long couponId;
        private final Long orderId;
        private boolean released;
        private CouponRollbackRelease(Long couponId, Long orderId) { this.couponId = couponId; this.orderId = orderId; }
        private void release() {
            if (!released) { couponCheckoutClient.release(couponId, orderId); released = true; }
        }
        @Override public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
                try { release(); }
                catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(OrderServiceImpl.class).error(
                            "Coupon release failed; reconciliation required: orderId={}, couponId={}", orderId, couponId, failure);
                }
            }
        }
    }

    private Order requireOwnedOrder(Order order) {
        if (order == null || UserContext.getUser() == null || !UserContext.getUser().equals(order.getUserId())) {
            throw new BadRequestException("订单不存在");
        }
        return order;
    }

    private List<OrderDetail> checkoutDetails(Long orderId) {
        List<OrderDetail> details = detailService.list(new QueryWrapper<OrderDetail>().eq("order_id", orderId));
        if (details == null || details.isEmpty()) {
            throw new BadRequestException("订单商品快照不存在");
        }
        return details;
    }

    @Override
    public OrderVO queryOrderById(Long orderId) {
        Order order = getById(orderId);
        Long userId = UserContext.getUser();
        if (order == null || userId == null || !userId.equals(order.getUserId())) {
            throw new BadRequestException("订单不存在");
        }
        OrderVO result = BeanUtils.copyBean(order, OrderVO.class);
        OrderLogistics logistics = logisticsService.getById(orderId);
        if (logistics == null) {
            return result;
        }
        ShippingAddressSnapshotVO address = new ShippingAddressSnapshotVO();
        address.setContact(logistics.getContact());
        address.setMobile(logistics.getMobile());
        address.setProvince(logistics.getProvince());
        address.setCity(logistics.getCity());
        address.setTown(logistics.getTown());
        address.setStreet(logistics.getStreet());

        OrderDeliveryVO delivery = new OrderDeliveryVO();
        delivery.setCarrier(logistics.getLogisticsCompany());
        delivery.setTrackingNumber(logistics.getLogisticsNumber());
        delivery.setAddressSnapshot(address);
        result.setDelivery(delivery);
        return result;
    }

    @Override
    public Integer queryOrderStatus(Long orderId) {
        Long userId = UserContext.getUser();
        if (orderId == null || userId == null) {
            throw new BadRequestException("订单状态查询参数不合法");
        }
        Order order = baseMapper.selectOne(new QueryWrapper<Order>()
                .eq("id", orderId)
                .eq("user_id", userId));
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        return order.getStatus();
    }

    @Override
    public PageDTO<OrderVO> queryMyOrders(PageQuery query, Integer status) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
        if (status != null && (status < 1 || status > 6)) {
            throw new BadRequestException("订单状态无效");
        }
        QueryWrapper<Order> wrapper = new QueryWrapper<Order>()
                .eq("user_id", userId)
                .eq(status != null, "status", status)
                .orderByDesc("create_time", "id");
        Page<Order> page = page(query.toMpPage(), wrapper);
        return PageDTO.of(page, OrderVO.class);
    }

    @Override
    @GlobalTransactional(name = "trade-cancel-order")
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = requireOwnedOrder(lockOrder(orderId));
        if (order == null || !UserContext.getUser().equals(order.getUserId())) {
            throw new BadRequestException("订单不存在");
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            throw new BadRequestException("只有待付款订单可以取消");
        }
        if (!markOrderCancelled(order)) {
            throw new BadRequestException("订单状态已变更，取消失败");
        }
        if (order.getCouponId() != null) {
            couponCheckoutClient.release(order.getCouponId(), order.getId());
        }
    }

    public boolean markOrderCancelled(Order order) {
        return lambdaUpdate()
                .set(Order::getStatus, 5)
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, order.getId())
                .eq(Order::getUserId, order.getUserId())
                .eq(Order::getStatus, 1)
                .update();
    }

    private int calculatePayable(int originalFee, int discountBasisPoints) {
        if (discountBasisPoints <= 0 || discountBasisPoints > FULL_PRICE_BASIS_POINTS) {
            throw new IllegalArgumentException("会员折扣参数无效");
        }
        return Math.toIntExact((originalFee * (long) discountBasisPoints + FULL_PRICE_BASIS_POINTS - 1)
                / FULL_PRICE_BASIS_POINTS);
    }

    private OrderLogistics buildLogistics(Long orderId, AddressDTO address) {
        return new OrderLogistics()
                .setOrderId(orderId)
                .setContact(address.getContact())
                .setMobile(address.getMobile())
                .setProvince(address.getProvince())
                .setCity(address.getCity())
                .setTown(address.getTown())
                .setStreet(address.getStreet());
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
