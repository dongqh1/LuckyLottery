package com.dongqh.payservice.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dongqh.payservice.domain.dto.PayApplyDTO;
import com.dongqh.payservice.domain.dto.PayOrderFormDTO;
import com.dongqh.payservice.domain.po.PayOrder;

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
