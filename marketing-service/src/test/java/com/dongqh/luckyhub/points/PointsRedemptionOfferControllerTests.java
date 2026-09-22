package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.points.controller.PointsRedemptionOfferController;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionOfferView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsRedemptionOfferControllerTests {
    @Mock private PointsRedemptionOfferService service;

    @Test
    void listsAvailableHmallOffersAndCreatesAdminQuote() throws Exception {
        PointsRedemptionOfferController controller = new PointsRedemptionOfferController(service);
        PointsRedemptionOfferView offer = new PointsRedemptionOfferView(
                71L, 317578L, 30L, 5, "运动休闲鞋", "/img/317578.jpg", 9900);
        CreatePointsRedemptionOfferCommand command =
                new CreatePointsRedemptionOfferCommand(317578L, 30L, 5);
        when(service.listAvailable()).thenReturn(List.of(offer));
        when(service.create(command)).thenReturn(offer);

        assertThat(controller.list().data()).containsExactly(offer);
        assertThat(controller.create(command).data()).isEqualTo(offer);
        verify(service).listAvailable();
        verify(service).create(command);

        Method list = PointsRedemptionOfferController.class.getMethod("list");
        assertThat(list.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/points/offers");
        Method create = PointsRedemptionOfferController.class.getMethod(
                "create", CreatePointsRedemptionOfferCommand.class);
        assertThat(create.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/admin/points/offers");
        assertThat(create.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.CREATED);
    }
}
