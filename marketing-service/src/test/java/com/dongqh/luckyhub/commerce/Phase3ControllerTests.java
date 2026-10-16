package com.dongqh.luckyhub.commerce;

import com.dongqh.luckyhub.domain.model.auth.JwtPayload;
import com.dongqh.luckyhub.security.auth.JwtService;
import com.dongqh.luckyhub.security.auth.SessionService;
import com.dongqh.luckyhub.service.coupon.CouponService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.domain.channel.inventory.service.ChannelInventoryService;
import com.dongqh.luckyhub.service.membership.MembershipService;
import com.dongqh.luckyhub.mapper.order.MallOrderMapper;
import com.dongqh.luckyhub.service.order.CashOrderService;
import com.dongqh.luckyhub.domain.po.payment.PaymentOrder;
import com.dongqh.luckyhub.mapper.payment.PaymentOrderMapper;
import com.dongqh.luckyhub.service.payment.PaymentService;
import com.dongqh.luckyhub.service.payment.impl.PaymentServiceImpl;
import com.dongqh.luckyhub.domain.constant.rbac.PermissionCodes;
import com.dongqh.luckyhub.service.rbac.UserPermissionService;
import com.dongqh.luckyhub.service.shipping.ShippingOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
class Phase3ControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean CouponService coupons;
    @MockitoBean MembershipService memberships;
    @MockitoBean CashOrderService orders;
    @MockitoBean PaymentService payments;
    @MockitoBean JwtService jwt;
    @MockitoBean SessionService sessions;
    @MockitoBean UserPermissionService permissions;

    @BeforeEach void login() {
        when(jwt.parse("token")).thenReturn(new JwtPayload(77L, "buyer", "s1"));
        when(sessions.isValid("s1", 77L)).thenReturn(true);
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(
                PermissionCodes.COUPON_READ, PermissionCodes.MEMBERSHIP_READ,
                PermissionCodes.ORDER_CREATE, PermissionCodes.ORDER_READ,
                PermissionCodes.ORDER_CANCEL, PermissionCodes.PAYMENT_CREATE));
    }

    @Test void exposesSelfScopedReadsAndCommands() throws Exception {
        when(memberships.getMine(77L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/coupons").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/memberships/me").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/orders").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"O-1\",\"skuId\":1,\"quantity\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/payments").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"P-1\",\"orderNo\":\"O-1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/payments/P-1/simulate-success").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(memberships).getMine(77L);
        verify(orders).create(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(payments).create(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(payments).simulateForUser(77L, "P-1");
    }

    @Test void callbackUsesSignatureInsteadOfJwt() throws Exception {
        mvc.perform(post("/callbacks/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"P-1\",\"result\":\"SUCCESS\",\"signature\":\"signed\"}"))
                .andExpect(status().isOk());
        verify(payments).callback(org.mockito.ArgumentMatchers.any());
    }

    @Test void userSimulationRejectsPaymentOwnedByAnotherUser() {
        PaymentOrderMapper paymentMapper = mock(PaymentOrderMapper.class);
        PaymentService service = new PaymentServiceImpl(paymentMapper, mock(MallOrderMapper.class),
                mock(ChannelInventoryService.class), mock(CouponService.class),
                mock(ShippingOrderService.class));
        when(paymentMapper.selectOne(any())).thenReturn(paymentOwnedBy(99L));

        assertThatThrownBy(() -> service.simulateForUser(7L, "DEMO-PAY-OTHER"))
                .isInstanceOf(BusinessException.class);
    }

    private PaymentOrder paymentOwnedBy(long userId) {
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo("DEMO-PAY-OTHER");
        payment.setUserId(userId);
        payment.setAmountCent(100L);
        return payment;
    }
}
