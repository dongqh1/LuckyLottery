package com.dongqh.lottery.benefit;

import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.dto.ShippingOrderDTO;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.domain.vo.benefit.BenefitView;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.service.benefit.LotteryBenefitQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotteryBenefitQueryServiceTests {

    @Test
    void mapsShippingNumberAndStatusFromLogisticsService() {
        UserBenefitMapper benefits = mock(UserBenefitMapper.class);
        LogisticsClient logistics = mock(LogisticsClient.class);
        UserBenefitRow benefit = new UserBenefitRow();
        benefit.setId(41L);
        benefit.setUserId(7L);
        benefit.setShippingOrderId(88L);
        when(benefits.selectMinePage(7L, null, null, null, null, 20L, 0L))
                .thenReturn(List.of(benefit));
        when(benefits.countMine(7L, null, null, null, null)).thenReturn(1L);
        when(logistics.getShippingOrdersByIds(List.of(88L))).thenReturn(List.of(
                new ShippingOrderDTO(88L, "SHIP-88", "LOGISTICS-88", "IN_TRANSIT",
                        null, null, "YTO", "圆通速递", "WB-88", null, null,
                        null, null, null, null, null, null, null)));

        BenefitView view = new LotteryBenefitQueryService(benefits, logistics)
                .pageMine(7L, 1L, 20L, null, null, null, null).records().get(0);

        assertThat(view.shippingNo()).isEqualTo("SHIP-88");
        assertThat(view.shippingStatus()).isEqualTo("IN_TRANSIT");
    }
}
