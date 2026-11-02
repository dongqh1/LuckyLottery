package com.dongqh.pay.controller;

import com.dongqh.pay.domain.dto.PayApplyDTO;
import com.dongqh.pay.domain.dto.PayOrderFormDTO;
import com.dongqh.pay.enums.PayType;
import com.dongqh.pay.service.IPayOrderService;
import com.hmall.common.exception.BizIllegalException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Api(tags = "支付相关接口")
@RestController
@RequestMapping("pay-orders")
@RequiredArgsConstructor
public class PayController {

    private final IPayOrderService payOrderService;

    @ApiOperation("生成支付单")
    @PostMapping
    public String applyPayOrder(@RequestBody PayApplyDTO applyDTO){
        if(!PayType.BALANCE.equalsValue(applyDTO.getPayType())){
            // 目前只支持余额支付
            throw new BizIllegalException("抱歉，目前只支持余额支付");
        }
        return payOrderService.applyPayOrder(applyDTO);
    }

    @ApiOperation("尝试基于用户余额支付")
    @ApiImplicitParam(value = "支付单id", name = "id")
    @PostMapping("{id}")
    public void tryPayOrderByBalance(@PathVariable("id") Long id, @RequestBody PayOrderFormDTO payOrderFormDTO){
        payOrderFormDTO.setId(id);
        payOrderService.tryPayOrderByBalance(payOrderFormDTO);
    }

    @ApiOperation("查询当前用户支付单状态")
    @GetMapping("/{payOrderId}/status")
    public Integer queryPayStatus(@PathVariable("payOrderId") Long payOrderId) {
        return payOrderService.queryPayStatus(payOrderId);
    }

    @ApiOperation("同步待支付单金额")
    @PutMapping("/biz/{orderId}/pending-amount")
    public void syncPendingAmount(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId,
            @RequestParam("amount") Integer amount) {
        payOrderService.syncPendingAmount(orderId, userId, amount);
    }
}
