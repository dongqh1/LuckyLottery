package com.dongqh.pointsservice.points;

import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOffer;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.exception.PointsDomainException;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOfferMapper;
import com.dongqh.luckyhub.points.service.impl.PointsRedemptionOfferServiceImpl;
import com.dongqh.luckyhub.points.service.port.PointsItemCatalogPort;
import com.dongqh.luckyhub.points.service.port.PointsItemSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PointsRedemptionOfferExtractionTests {

    private final PointsItemCatalogPort catalog = mock(PointsItemCatalogPort.class);
    private final PointsRedemptionOfferMapper mapper = mock(PointsRedemptionOfferMapper.class);
    private final PointsRedemptionOfferServiceImpl service =
            new PointsRedemptionOfferServiceImpl(catalog, mapper);

    @Test
    void createsOfferFromAuthoritativeItemSnapshot() {
        when(catalog.getItem(1001L)).thenReturn(Optional.of(item(1001L, 8, 1)));
        doAnswer(invocation -> {
            PointsRedemptionOffer offer = invocation.getArgument(0);
            offer.setId(71L);
            return 1;
        }).when(mapper).insert(any(PointsRedemptionOffer.class));

        var result = service.create(new CreatePointsRedemptionOfferCommand(1001L, 3_000L, 5));

        assertThat(result.id()).isEqualTo(71L);
        assertThat(result.hmallItemId()).isEqualTo(1001L);
        assertThat(result.itemName()).isEqualTo("商城耳机");
        assertThat(result.cashPriceCent()).isEqualTo(9_900);
        ArgumentCaptor<PointsRedemptionOffer> saved = ArgumentCaptor.forClass(PointsRedemptionOffer.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getHmallItemId()).isEqualTo(1001L);
    }

    @Test
    void rejectsUnavailableOrInsufficientItemBeforeCreatingOffer() {
        when(catalog.getItem(1001L)).thenReturn(Optional.of(item(1001L, 4, 1)));

        assertThatThrownBy(() -> service.create(new CreatePointsRedemptionOfferCommand(1001L, 3_000L, 5)))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsItemServiceMissBeforeCreatingOffer() {
        when(catalog.getItem(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreatePointsRedemptionOfferCommand(1001L, 3_000L, 1)))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE));
        verifyNoInteractions(mapper);
    }

    private PointsItemSnapshot item(long id, int stock, int status) {
        return new PointsItemSnapshot(id, "商城耳机", 9_900, stock, "/imgs/1001.jpg", status);
    }
}
