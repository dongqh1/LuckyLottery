package com.dongqh.payservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dongqh.hmapi.client.TradeClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.hmapi.dto.OrderDomain;
import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.payservice.domain.dto.PayApplyDTO;
import com.dongqh.payservice.domain.dto.PayOrderFormDTO;
import com.dongqh.payservice.domain.po.PayOrder;
import com.dongqh.payservice.domain.po.PayOrderDomain;
import com.dongqh.payservice.enums.PayStatus;
import com.dongqh.payservice.mapper.PayOrderMapper;
import com.dongqh.payservice.mapper.PayOrderDomainMapper;
import com.dongqh.payservice.messaging.PayOutboxService;
import com.dongqh.payservice.service.IPayOrderService;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 支付订单 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-16
 */
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    private final UserClient userService;

    private final TradeClient tradeClient;

    private final PayOrderMapper payOrderMapper;

    private final PayOrderDomainMapper payOrderDomainMapper;

    private final PayOutboxService payOutboxService;

    @Value("${pay.membership-purchases.enabled:false}")
    private boolean membershipPurchasesEnabled;



    @Override
    @Transactional
    public String applyPayOrder(PayApplyDTO applyDTO) {
        if (applyDTO == null || applyDTO.getBizOrderNo() == null) throw new BizIllegalException("订单不能为空");
        Long currentUserId = UserContext.getUser();
        if (currentUserId == null) {
            throw new BizIllegalException("用户未登录");
        }
        OrderDomain domain = resolveDomain(applyDTO.getOrderDomain());
        requireMembershipPurchasesEnabled(domain);
        applyDTO.setOrderDomain(domain);
        OrderPaymentDTO order = queryBusinessOrder(domain, applyDTO.getBizOrderNo(), currentUserId);
        if (order == null || !currentUserId.equals(order.getUserId())
                || order.getOrderDomain() != domain
                || !Integer.valueOf(1).equals(order.getStatus()) || order.getTotalFee() == null || order.getTotalFee() <= 0
                || (domain == OrderDomain.MEMBERSHIP && order.getMembershipPurchase() == null)) {
            throw new BizIllegalException("业务订单不可支付");
        }
        applyDTO.setAmount(order.getTotalFee()); // Never accept browser pricing.
        // 1.幂等性校验
        PayOrder payOrder = checkIdempotent(applyDTO);
        // 2.返回结果
        return payOrder.getId().toString();
    }

    @Override
    @GlobalTransactional(name = "pay-by-balance")
    @Transactional
    public void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO) {
        if (payOrderFormDTO == null || payOrderFormDTO.getId() == null) {
            throw new BizIllegalException("支付单不能为空！");
        }

        PayOrder payOrder = getById(payOrderFormDTO.getId());
        if (payOrder == null) {
            throw new BizIllegalException("支付单不存在！");
        }

        Long currentUserId = UserContext.getUser();
        if (currentUserId == null || !currentUserId.equals(payOrder.getBizUserId())) {
            throw new BizIllegalException("无权支付该订单！");
        }
        OrderDomain domain = orderDomain(payOrder);
        requireMembershipPurchasesEnabled(domain);
        OrderPaymentDTO order = queryBusinessOrder(domain, payOrder.getBizOrderNo(), currentUserId);
        payOrder = baseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayOrder>()
                .eq("id", payOrderFormDTO.getId()).last("FOR UPDATE"));
        if (payOrder == null || !currentUserId.equals(payOrder.getBizUserId())
                || order == null || !payOrder.getBizOrderNo().equals(order.getId())
                || order.getOrderDomain() != domain) {
            throw new BizIllegalException("业务订单信息与支付单不一致！");
        }
        if (payOrder.getAmount() == null || payOrder.getAmount() <= 0) {
            throw new BizIllegalException("支付金额不合法！");
        }
        if (!PayStatus.WAIT_BUYER_PAY.equalsValue(payOrder.getStatus())) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        if (order == null
                || !currentUserId.equals(order.getUserId())
                || !Integer.valueOf(1).equals(order.getStatus())
                || !payOrder.getAmount().equals(order.getTotalFee())
                || (domain == OrderDomain.MEMBERSHIP && (order.getMembershipPurchase() == null
                || order.getCouponId() != null))) {
            throw new BizIllegalException("业务订单信息与支付单不一致！");
        }

        LocalDateTime paidAt = LocalDateTime.now();
        if (!markPayOrderSuccess(payOrder.getId(), paidAt)) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        // 后续任一步失败都会由当前事务回滚支付单 CAS。
        userService.deductMoney(payOrderFormDTO.getPw(), order.getTotalFee());

        payOutboxService.append(new PaySuccessMessage(
                UUID.randomUUID().toString(),
                order.getId(),
                order.getUserId(),
                order.getTotalFee(),
                order.getCouponId(),
                paidAt,
                domain,
                order.getMembershipPurchase()));
    }

    @Override
    @Transactional
    public void syncPendingAmount(Long orderId, Long userId, Integer amount) {
        if (orderId == null || userId == null || amount == null || amount <= 0) {
            throw new BizIllegalException("支付单金额同步参数不合法");
        }

        List<PayOrder> payments = payOrderMapper.selectList(
                new QueryWrapper<PayOrder>()
                        .eq("biz_order_no", orderId)
                        .last("FOR UPDATE"));
        for (PayOrder payment : payments) {
            if (orderDomain(payment) == OrderDomain.MEMBERSHIP) continue;
            if (!userId.equals(payment.getBizUserId())) {
                throw new BizIllegalException("无权修改该支付单");
            }
            if (!PayStatus.NOT_COMMIT.equalsValue(payment.getStatus())
                    && !PayStatus.WAIT_BUYER_PAY.equalsValue(payment.getStatus())) {
                throw new BizIllegalException("支付单状态不允许修改金额");
            }
            payment.setAmount(amount);
            if (!updateById(payment)) {
                throw new BizIllegalException("支付金额更新失败");
            }
        }
    }

    @Override
    public Integer queryPayStatus(Long payOrderId) {
        Long userId = UserContext.getUser();
        if (payOrderId == null || userId == null) {
            throw new BadRequestException("支付状态查询参数不合法");
        }
        PayOrder payOrder = payOrderMapper.selectOne(new QueryWrapper<PayOrder>()
                .eq("id", payOrderId)
                .eq("biz_user_id", userId));
        if (payOrder == null) {
            throw new BadRequestException("支付单不存在");
        }
        return payOrder.getStatus();
    }

    public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
        return lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())
                .set(PayOrder::getPaySuccessTime, successTime)
                .eq(PayOrder::getId, id)
                // 支付状态的乐观锁判断
                .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
                .update();
    }


    private PayOrder checkIdempotent(PayApplyDTO applyDTO) {
        // 1.首先查询支付单
        OrderDomain requestedDomain = resolveDomain(applyDTO.getOrderDomain());
        PayOrder oldOrder = queryByBizOrderNo(applyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            save(payOrder);
            persistOrderDomain(payOrder, requestedDomain);
            return payOrder;
        }
        if (orderDomain(oldOrder) != requestedDomain) {
            throw new BizIllegalException("业务订单类型与已有支付单不一致");
        }
        // 3.旧单已经存在，判断是否支付成功
        if (PayStatus.TRADE_SUCCESS.equalsValue(oldOrder.getStatus())) {
            // 已经支付成功，抛出异常
            throw new BizIllegalException("订单已经支付！");
        }
        // 4.旧单已经存在，判断是否已经关闭
        if (PayStatus.TRADE_CLOSED.equalsValue(oldOrder.getStatus())) {
            // 已经关闭，抛出异常
            throw new BizIllegalException("订单已关闭");
        }
        // 5.旧单已经存在，判断支付渠道是否一致
        if (!StringUtils.equals(oldOrder.getPayChannelCode(), applyDTO.getPayChannelCode())) {
            // 支付渠道不一致，需要重置数据，然后重新申请支付单
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setId(oldOrder.getId());
            payOrder.setQrCodeUrl("");
            updateById(payOrder);
            payOrder.setPayOrderNo(oldOrder.getPayOrderNo());
            return payOrder;
        }
        // Refresh any legacy waiting payment using the authoritative, locked business-order amount.
        if (!applyDTO.getAmount().equals(oldOrder.getAmount())) {
            if (!updateById(new PayOrder().setId(oldOrder.getId()).setAmount(applyDTO.getAmount()))) {
                throw new BizIllegalException("支付金额更新失败");
            }
            oldOrder.setAmount(applyDTO.getAmount());
        }
        return oldOrder;
    }

    private PayOrder buildPayOrder(PayApplyDTO payApplyDTO) {
        // 1.数据转换
        PayOrder payOrder = BeanUtils.toBean(payApplyDTO, PayOrder.class);
        // 2.初始化数据
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(120L));
        payOrder.setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
        payOrder.setBizUserId(UserContext.getUser());
        payOrder.setOrderDomain(resolveDomain(payApplyDTO.getOrderDomain()).name());
        return payOrder;
    }
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }

    private OrderDomain orderDomain(PayOrder order) {
        if (order == null || !membershipPurchasesEnabled) return OrderDomain.TRADE;
        PayOrderDomain domain = payOrderDomainMapper.selectByPayOrderId(order.getId());
        return domain == null ? OrderDomain.TRADE : resolveDomain(domain.getOrderDomain());
    }

    private void persistOrderDomain(PayOrder order, OrderDomain domain) {
        order.setOrderDomain(domain.name());
        if (domain != OrderDomain.MEMBERSHIP) return;
        PayOrderDomain mapping = new PayOrderDomain();
        mapping.setPayOrderId(order.getId());
        mapping.setBizOrderNo(order.getBizOrderNo());
        mapping.setOrderDomain(domain.name());
        mapping.setCreatedAt(LocalDateTime.now());
        payOrderDomainMapper.insert(mapping);
    }

    private OrderPaymentDTO queryBusinessOrder(OrderDomain domain, Long orderId, Long userId) {
        return domain == OrderDomain.MEMBERSHIP
                ? tradeClient.queryMembershipOrderForPayment(orderId, userId)
                : tradeClient.queryOrderForPayment(orderId, userId);
    }

    private OrderDomain resolveDomain(OrderDomain domain) {
        return domain == null ? OrderDomain.TRADE : domain;
    }

    private OrderDomain resolveDomain(String domain) {
        if (domain == null || domain.isBlank()) return OrderDomain.TRADE;
        try {
            return OrderDomain.valueOf(domain);
        } catch (IllegalArgumentException exception) {
            throw new BizIllegalException("业务订单类型无效");
        }
    }

    private void requireMembershipPurchasesEnabled(OrderDomain domain) {
        if (domain == OrderDomain.MEMBERSHIP && !membershipPurchasesEnabled) {
            throw new BizIllegalException("会员购买支付流程尚未启用");
        }
    }
}
