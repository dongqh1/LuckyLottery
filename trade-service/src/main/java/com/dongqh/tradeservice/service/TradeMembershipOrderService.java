package com.dongqh.tradeservice.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.MembershipPurchaseSnapshotDTO;
import com.dongqh.hmapi.dto.OrderDomain;
import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.tradeservice.domain.dto.CreateMembershipOrderRequest;
import com.dongqh.tradeservice.domain.po.TradeMembershipOrder;
import com.dongqh.tradeservice.mapper.TradeMembershipOrderMapper;
import com.hmall.common.exception.BadRequestException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class TradeMembershipOrderService {
    private final TradeMembershipOrderMapper orders;
    private final MembershipClient memberships;

    @Value("${trade.membership-purchases.enabled:false}")
    private boolean membershipPurchasesEnabled;

    public TradeMembershipOrderService(TradeMembershipOrderMapper orders, MembershipClient memberships) {
        this.orders = orders;
        this.memberships = memberships;
    }

    @Transactional
    public Long create(CreateMembershipOrderRequest request, Long userId) {
        requireEnabled();
        if (request == null || request.membershipProductId() == null || userId == null || userId <= 0
                || request.requestKey() == null || request.requestKey().isBlank()
                || request.requestKey().trim().length() > 64) {
            throw new BadRequestException("会员订单参数不合法");
        }
        String requestKey = request.requestKey().trim();
        TradeMembershipOrder existing = orders.selectByUserAndRequestKey(userId, requestKey);
        if (existing != null) return existingOrder(existing, request.membershipProductId());

        MembershipPurchaseSnapshotDTO snapshot = memberships.getPurchaseSnapshot(request.membershipProductId());
        if (snapshot == null || !request.membershipProductId().equals(snapshot.productId())
                || snapshot.priceCent() > Integer.MAX_VALUE) {
            throw new BadRequestException("会员产品不可购买");
        }

        TradeMembershipOrder order = new TradeMembershipOrder();
        order.setId(IdWorker.getId(order));
        order.setUserId(userId);
        order.setRequestKey(requestKey);
        order.setMembershipProductId(snapshot.productId());
        order.setProductCode(snapshot.productCode());
        order.setProductName(snapshot.productName());
        order.setMembershipLevel(snapshot.membershipLevel());
        order.setDurationDays(snapshot.durationDays());
        order.setTotalFee(Math.toIntExact(snapshot.priceCent()));
        order.setDiscountBasisPoints(snapshot.discountBasisPoints());
        order.setDailyDrawBonus(snapshot.dailyDrawBonus());
        order.setPointsMultiplierBasisPoints(snapshot.pointsMultiplierBasisPoints());
        order.setStatus(1);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(order.getCreatedAt());
        try {
            orders.insert(order);
            return order.getId();
        } catch (DuplicateKeyException duplicate) {
            TradeMembershipOrder raced = orders.selectByUserAndRequestKey(userId, requestKey);
            if (raced == null) throw duplicate;
            return existingOrder(raced, request.membershipProductId());
        }
    }

    @Transactional(readOnly = true)
    public OrderPaymentDTO paymentSnapshot(Long orderId, Long userId) {
        requireEnabled();
        if (orderId == null || userId == null) throw new BadRequestException("订单支付查询参数不合法");
        TradeMembershipOrder order = orders.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) throw new BadRequestException("订单不存在");
        return paymentView(order);
    }

    @Transactional
    public void markPaid(PaySuccessMessage event) {
        if (event == null || event.orderDomain() != OrderDomain.MEMBERSHIP || event.membershipPurchase() == null) {
            throw new BadRequestException("会员支付事件不合法");
        }
        TradeMembershipOrder order = orders.selectByIdForUpdate(event.orderId());
        if (order == null || !event.userId().equals(order.getUserId())
                || !event.amount().equals(order.getTotalFee())
                || !snapshot(order).equals(event.membershipPurchase())) {
            throw new BadRequestException("会员支付事件与订单快照不一致");
        }
        if (Integer.valueOf(2).equals(order.getStatus())) return;
        if (!Integer.valueOf(1).equals(order.getStatus())) throw new BadRequestException("会员订单状态不可支付");
        order.setStatus(2);
        order.setPaidAt(event.paidAt());
        order.setUpdatedAt(LocalDateTime.now());
        if (orders.updateById(order) != 1) throw new BadRequestException("会员订单支付状态更新失败");
    }

    private Long existingOrder(TradeMembershipOrder order, Long productId) {
        if (!Objects.equals(order.getMembershipProductId(), productId)) {
            throw new BadRequestException("会员订单幂等键冲突");
        }
        return order.getId();
    }

    private OrderPaymentDTO paymentView(TradeMembershipOrder order) {
        OrderPaymentDTO result = new OrderPaymentDTO();
        result.setId(order.getId());
        result.setUserId(order.getUserId());
        result.setStatus(order.getStatus());
        result.setTotalFee(order.getTotalFee());
        result.setOrderDomain(OrderDomain.MEMBERSHIP);
        result.setMembershipPurchase(snapshot(order));
        return result;
    }

    private MembershipPurchaseSnapshotDTO snapshot(TradeMembershipOrder order) {
        return new MembershipPurchaseSnapshotDTO(order.getMembershipProductId(), order.getProductCode(),
                order.getProductName(), order.getMembershipLevel(), order.getDurationDays(),
                order.getTotalFee().longValue(), order.getDiscountBasisPoints(), order.getDailyDrawBonus(),
                order.getPointsMultiplierBasisPoints());
    }

    private void requireEnabled() {
        if (!membershipPurchasesEnabled) throw new BadRequestException("会员购买流程尚未启用");
    }
}
