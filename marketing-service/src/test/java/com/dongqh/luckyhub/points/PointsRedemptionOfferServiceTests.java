package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.domain.hmall.integration.HmallCatalogClient;
import com.dongqh.luckyhub.domain.hmall.integration.HmallItemView;
import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.domain.po.points.PointsRedemptionOffer;
import com.dongqh.luckyhub.mapper.points.PointsRedemptionOfferMapper;
import com.dongqh.luckyhub.service.points.impl.PointsRedemptionOfferServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsRedemptionOfferServiceTests {

    private final HmallCatalogClient hmall = mock(HmallCatalogClient.class);
    private final PointsRedemptionOfferMapper mapper = mock(PointsRedemptionOfferMapper.class);
    private final PointsRedemptionOfferServiceImpl service =
            new PointsRedemptionOfferServiceImpl(hmall, mapper);

    @Test
    void createsMarketingQuoteBoundToAuthoritativeHmallItemId() {
        when(hmall.getItem(1001L)).thenReturn(item(1001L, 8));
        doAnswer(invocation -> {
            PointsRedemptionOffer offer = invocation.getArgument(0);
            offer.setId(71L);
            return 1;
        }).when(mapper).insert(any(PointsRedemptionOffer.class));

        var result = service.create(new CreatePointsRedemptionOfferCommand(1001L, 3_000L, 5));

        assertThat(result.id()).isEqualTo(71L);
        assertThat(result.hmallItemId()).isEqualTo(1001L);
        assertThat(result.unitPoints()).isEqualTo(3_000L);
        assertThat(result.availableStock()).isEqualTo(5);
        assertThat(result.itemName()).isEqualTo("商城耳机");
        ArgumentCaptor<PointsRedemptionOffer> saved = ArgumentCaptor.forClass(PointsRedemptionOffer.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getHmallItemId()).isEqualTo(1001L);
    }

    private HmallItemView item(long id, int stock) {
        return new HmallItemView(id, "商城耳机", 9_900, stock,
                "/imgs/1001.jpg", "数码", "HMall", "黑色", 1);
    }
}
