package com.hmall.controller;

import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.domain.vo.OrderVO;
import com.hmall.service.IOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderCheckoutControllerTest {
    IOrderService orders = mock(IOrderService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(orders)).build();
    @Test void checkoutItemsContractIsItemIdAndNum() throws Exception {
        when(orders.queryCheckoutItems(200L)).thenReturn(List.of(new OrderDetailDTO().setItemId(1001L).setNum(2)));
        mvc.perform(get("/orders/200/checkout-items")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemId").value(1001)).andExpect(jsonPath("$[0].num").value(2));
    }
    @Test void stringCouponIdKeepsLongPrecisionAndReturnsLatestOrder() throws Exception {
        OrderVO latest = new OrderVO(); latest.setId(200L); latest.setTotalFee(8500);
        when(orders.applyCheckoutCoupon(200L,9007199254740993L)).thenReturn(latest);
        mvc.perform(put("/orders/200/coupon").contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponId\":\"9007199254740993\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalFee").value(8500));
        verify(orders).applyCheckoutCoupon(200L,9007199254740993L);
    }
    @Test void missingOrInvalidCouponIdIsBadRequest() throws Exception {
        for (String body : List.of("{}", "{\"couponId\":\"-1\"}","{\"couponId\":\"abc\"}")) {
            mvc.perform(put("/orders/200/coupon").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders);
    }
}
