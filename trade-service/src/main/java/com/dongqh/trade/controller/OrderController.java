package com.dongqh.trade.controller;

import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.trade.domain.dto.OrderCouponFormDTO;
import com.dongqh.trade.domain.dto.OrderFormDTO;
import com.dongqh.trade.domain.dto.CreateMembershipOrderRequest;
import com.dongqh.trade.domain.vo.OrderVO;
import com.dongqh.trade.service.TradeMembershipOrderService;
import com.hmall.common.utils.UserContext;
import com.dongqh.trade.service.IOrderService;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Api(tags = "订单管理接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;
    private final TradeMembershipOrderService membershipOrders;

    @ApiOperation("根据id查询订单")
    @GetMapping("{id}")
    public OrderVO queryOrderById(@Param ("订单id")@PathVariable("id") Long orderId) {
        return orderService.queryOrderById(orderId);
    }

    @ApiOperation("查询当前用户订单状态")
    @GetMapping("/{orderId}/status")
    public Integer queryOrderStatus(@PathVariable("orderId") Long orderId) {
        return orderService.queryOrderStatus(orderId);
    }

    @ApiOperation("查询当前用户订单的收银台商品快照")
    @GetMapping("/{id}/checkout-items")
    public java.util.List<OrderDetailDTO> queryCheckoutItems(@PathVariable("id") Long orderId) {
        return orderService.queryCheckoutItems(orderId);
    }

    @ApiOperation("为尚未选券的待付款订单补选优惠券")
    @PutMapping("/{id}/coupon")
    public OrderVO applyCheckoutCoupon(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderCouponFormDTO form) {
        return orderService.applyCheckoutCoupon(orderId, Long.valueOf(form.getCouponId()));
    }

    @ApiOperation("分页查询当前用户的订单")
    @GetMapping("/me")
    public PageDTO<OrderVO> queryMyOrders(@Valid PageQuery query,
                                          @RequestParam(required = false) Integer status) {
        return orderService.queryMyOrders(query, status);
    }

    @ApiOperation("创建订单")
    @PostMapping
    public Long createOrder(@RequestBody OrderFormDTO orderFormDTO){
        return orderService.createOrder(orderFormDTO);
    }

    @ApiOperation("创建会员购买订单")
    @PostMapping("/memberships")
    public Long createMembershipOrder(@Valid @RequestBody CreateMembershipOrderRequest request) {
        return membershipOrders.create(request, UserContext.getUser());
    }

    @ApiOperation("标记订单已支付")
    @ApiImplicitParam(name = "orderId", value = "订单id", paramType = "path")
    @PutMapping("/{orderId}/pay-success")
    public void markOrderPaySuccess(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId,
            @RequestParam("amount") Integer amount) {
        orderService.markOrderPaySuccess(orderId, userId, amount);
    }

    @ApiOperation("查询订单支付快照")
    @GetMapping("/{orderId}/payment")
    public OrderPaymentDTO queryOrderForPayment(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId) {
        return orderService.queryOrderForPayment(orderId, userId);
    }

    @ApiOperation("取消当前用户的待付款订单")
    @DeleteMapping("/{orderId}")
    public void cancelOrder(@PathVariable("orderId") Long orderId) {
        orderService.cancelOrder(orderId);
    }
}
