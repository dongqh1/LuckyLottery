package com.dongqh.pay.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dongqh.pay.domain.dto.PayApplyDTO;
import com.dongqh.pay.domain.dto.PayOrderFormDTO;
import com.dongqh.pay.domain.po.PayOrder;

/**
 * <p>
 * 支付订单 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-16
 */
public interface IPayOrderService extends IService<PayOrder> {

    String applyPayOrder(PayApplyDTO applyDTO);

    void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO);

    void syncPendingAmount(Long orderId, Long userId, Integer amount);

    Integer queryPayStatus(Long payOrderId);
}
