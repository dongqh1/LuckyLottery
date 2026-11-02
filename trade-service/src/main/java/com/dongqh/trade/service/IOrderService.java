package com.dongqh.trade.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.trade.domain.dto.OrderFormDTO;
import com.dongqh.trade.domain.po.Order;
import com.dongqh.trade.domain.vo.OrderVO;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface IOrderService extends IService<Order> {

    Long createOrder(OrderFormDTO orderFormDTO);

    /** Must be called within the caller's transaction; shared checkout/payment lock. */
    Order lockOrder(Long orderId);

    java.util.List<OrderDetailDTO> queryCheckoutItems(Long orderId);

    OrderVO applyCheckoutCoupon(Long orderId, Long couponId);

    OrderPaymentDTO queryOrderForPayment(Long orderId, Long userId);

    void markOrderPaySuccess(Long orderId, Long userId, Integer amount);

    void markOrderPaySuccess(Long orderId, Long userId, Integer amount,
                             java.time.LocalDateTime paidAt);

    void cancelOrder(Long orderId);

    OrderVO queryOrderById(Long orderId);

    Integer queryOrderStatus(Long orderId);

    PageDTO<OrderVO> queryMyOrders(PageQuery query, Integer status);
}
