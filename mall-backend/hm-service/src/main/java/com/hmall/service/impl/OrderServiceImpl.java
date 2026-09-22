package com.hmall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.ItemDTO;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.domain.dto.OrderFormDTO;
import com.hmall.domain.po.Address;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderDetail;
import com.hmall.domain.po.OrderLogistics;
import com.hmall.domain.vo.OrderDeliveryVO;
import com.hmall.domain.vo.OrderVO;
import com.hmall.domain.vo.ShippingAddressSnapshotVO;
import com.hmall.integration.marketing.MembershipBenefitClient;
import com.hmall.integration.marketing.MembershipBenefitView;
import com.hmall.integration.marketing.CouponCheckoutClient;
import com.hmall.integration.marketing.CouponReservationView;
import com.hmall.mapper.OrderMapper;
import com.hmall.service.IAddressService;
import com.hmall.service.ICartService;
import com.hmall.service.IItemService;
import com.hmall.service.IOrderDetailService;
import com.hmall.service.IOrderLogisticsService;
import com.hmall.service.IOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单服务实现类。
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final int FULL_PRICE_BASIS_POINTS = 10_000;

    private final IItemService itemService;
    private final IOrderDetailService detailService;
    private final ICartService cartService;
    private final IAddressService addressService;
    private final IOrderLogisticsService logisticsService;
    private final MembershipBenefitClient membershipBenefitClient;
    private final CouponCheckoutClient couponCheckoutClient;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hmall.mapper.PayOrderMapper payOrderMapper;

    public OrderServiceImpl(IItemService itemService,
                            IOrderDetailService detailService,
                            ICartService cartService,
                            IAddressService addressService,
                            IOrderLogisticsService logisticsService,
                            MembershipBenefitClient membershipBenefitClient,
                            CouponCheckoutClient couponCheckoutClient) {
        this.itemService = itemService;
        this.detailService = detailService;
        this.cartService = cartService;
        this.addressService = addressService;
        this.logisticsService = logisticsService;
        this.membershipBenefitClient = membershipBenefitClient;
        this.couponCheckoutClient = couponCheckoutClient;
    }

    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        Order order = new Order();
        Long userId = UserContext.getUser();
        Address address = orderFormDTO.getAddressId() == null
                ? null : addressService.getById(orderFormDTO.getAddressId());
        if (address == null || userId == null || !userId.equals(address.getUserId())) {
            throw new BadRequestException("收货地址不存在");
        }
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        List<ItemDTO> items = itemService.queryItemByIds(itemIds);
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
            cartService.removeByItemIds(itemIds);
            try {
                itemService.deductStock(detailDTOS);
            } catch (Exception e) {
                throw new RuntimeException("库存不足！");
            }
            return order.getId();
        } catch (RuntimeException exception) {
            if (coupon != null) {
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
    public void markOrderPaySuccess(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    public Order lockOrder(Long orderId) {
        return baseMapper.selectByIdForUpdate(orderId);
    }

    @Override
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
        // Always acquire business order before payment rows, matching balance payment.
        List<com.hmall.domain.po.PayOrder> payments = payOrderMapper.selectList(
                new QueryWrapper<com.hmall.domain.po.PayOrder>().eq("biz_order_no", orderId).last("FOR UPDATE"));
        for (com.hmall.domain.po.PayOrder payment : payments) {
            if (!order.getUserId().equals(payment.getBizUserId())
                    || !(com.hmall.enums.PayStatus.NOT_COMMIT.equalsValue(payment.getStatus())
                    || com.hmall.enums.PayStatus.WAIT_BUYER_PAY.equalsValue(payment.getStatus()))) {
                throw new BadRequestException("支付单状态不允许补选优惠券");
            }
        }
        List<OrderDetailDTO> checkoutItems = validateCheckoutSnapshot(order);
        // Register BEFORE reserve: timeout may mean the remote side has already locked the coupon.
        // User identity is carried by the authenticated AuthorizationContext; orderId is sent explicitly.
        CouponRollbackRelease release = new CouponRollbackRelease(couponId, orderId);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
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
            for (com.hmall.domain.po.PayOrder payment : payments) {
                if (payOrderMapper.updateById(new com.hmall.domain.po.PayOrder()
                        .setId(payment.getId()).setAmount(order.getTotalFee())) != 1) {
                    throw new BadRequestException("支付金额更新失败");
                }
            }
            return BeanUtils.copyBean(order, OrderVO.class);
        } catch (RuntimeException | Error failure) {
            try { release.release(); } catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
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
        List<ItemDTO> catalog = itemService.queryItemByIds(ids);
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

    private OrderLogistics buildLogistics(Long orderId, Address address) {
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
