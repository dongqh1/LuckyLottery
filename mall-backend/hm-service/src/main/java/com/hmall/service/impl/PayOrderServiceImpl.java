package com.hmall.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.PayApplyDTO;
import com.hmall.domain.dto.PayOrderFormDTO;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.PayOrder;
import com.hmall.enums.PayStatus;
import com.hmall.mapper.PayOrderMapper;
import com.hmall.integration.marketing.CouponCheckoutClient;
import com.hmall.service.IOrderService;
import com.hmall.service.IPayOrderService;
import com.hmall.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    private final IUserService userService;

    private final IOrderService orderService;

    private final CouponCheckoutClient couponCheckoutClient;

    @Override
    @Transactional
    public String applyPayOrder(PayApplyDTO applyDTO) {
        if (applyDTO == null || applyDTO.getBizOrderNo() == null) throw new BizIllegalException("订单不能为空");
        Order order = orderService.lockOrder(applyDTO.getBizOrderNo());
        if (order == null || UserContext.getUser() == null || !UserContext.getUser().equals(order.getUserId())
                || !Integer.valueOf(1).equals(order.getStatus()) || order.getTotalFee() == null || order.getTotalFee() <= 0) {
            throw new BizIllegalException("业务订单不可支付");
        }
        applyDTO.setAmount(order.getTotalFee()); // Never accept browser pricing.
        // 1.幂等性校验
        PayOrder payOrder = checkIdempotent(applyDTO);
        // 2.返回结果
        return payOrder.getId().toString();
    }

    @Override
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
        // The initial non-locking lookup is for identity only; it can predate a coupon commit.
        Order order = orderService.lockOrder(payOrder.getBizOrderNo());
        payOrder = baseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayOrder>()
                .eq("id", payOrderFormDTO.getId()).last("FOR UPDATE"));
        if (payOrder == null || !currentUserId.equals(payOrder.getBizUserId())
                || order == null || !order.getId().equals(payOrder.getBizOrderNo())) {
            throw new BizIllegalException("业务订单信息与支付单不一致！");
        }
        if (payOrder.getAmount() == null || payOrder.getAmount() <= 0) {
            throw new BizIllegalException("支付金额不合法！");
        }
        if (!PayStatus.WAIT_BUYER_PAY.equalsValue(payOrder.getStatus())) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        // order was loaded with a current locking read above.
        if (order == null
                || !currentUserId.equals(order.getUserId())
                || !Integer.valueOf(1).equals(order.getStatus())
                || !payOrder.getAmount().equals(order.getTotalFee())) {
            throw new BizIllegalException("业务订单信息与支付单不一致！");
        }

        LocalDateTime paidAt = LocalDateTime.now();
        if (!markPayOrderSuccess(payOrder.getId(), paidAt)) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        // 后续任一步失败都会由当前事务回滚支付单 CAS。
        userService.deductMoney(payOrderFormDTO.getPw(), order.getTotalFee());
        if (!markBusinessOrderPaid(order, paidAt)) {
            throw new BizIllegalException("订单状态已变更，支付失败！");
        }
        if (order.getCouponId() != null) {
            couponCheckoutClient.use(order.getCouponId(), order.getId());
        }
    }

    public boolean markBusinessOrderPaid(Order order, LocalDateTime paidAt) {
        return orderService.lambdaUpdate()
                .set(Order::getStatus, 2)
                .set(Order::getPayTime, paidAt)
                .eq(Order::getId, order.getId())
                .eq(Order::getUserId, order.getUserId())
                .eq(Order::getTotalFee, order.getTotalFee())
                .eq(Order::getStatus, 1)
                .update();
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
        PayOrder oldOrder = queryByBizOrderNo(applyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            save(payOrder);
            return payOrder;
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
        return payOrder;
    }
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }
}
