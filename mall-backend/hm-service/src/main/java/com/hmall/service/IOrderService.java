package com.hmall.service;

import com.hmall.domain.dto.OrderFormDTO;
import com.hmall.domain.po.Order;
import com.hmall.domain.vo.OrderVO;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.baomidou.mybatisplus.spring.service.IService;

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

    java.util.List<com.hmall.domain.dto.OrderDetailDTO> queryCheckoutItems(Long orderId);

    OrderVO applyCheckoutCoupon(Long orderId, Long couponId);

    void markOrderPaySuccess(Long orderId);

    void cancelOrder(Long orderId);

    OrderVO queryOrderById(Long orderId);

    PageDTO<OrderVO> queryMyOrders(PageQuery query, Integer status);
}
