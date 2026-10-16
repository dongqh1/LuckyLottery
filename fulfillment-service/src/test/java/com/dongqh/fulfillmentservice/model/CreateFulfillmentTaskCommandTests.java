package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateFulfillmentTaskCommandTests {

    @Test
    void trimsIdentityFieldsAndDefaultsAttemptLimit() {
        CreateFulfillmentTaskCommand command = new CreateFulfillmentTaskCommand(
                " TASK-1 ",
                " LOTTERY_REWARD ",
                " DRAW-1 ",
                FulfillmentType.COUPON,
                11L,
                new CouponFulfillmentPayload("SUMMER", 1),
                null
        );

        assertThat(command.fulfillmentNo()).isEqualTo("TASK-1");
        assertThat(command.sourceType()).isEqualTo("LOTTERY_REWARD");
        assertThat(command.sourceId()).isEqualTo("DRAW-1");
        assertThat(command.maxAttempts()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidIdentityTypeUserAndAttemptLimit() {
        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                " ", "LOTTERY_REWARD", "DRAW-1", FulfillmentType.COUPON, 11L,
                new CouponFulfillmentPayload("SUMMER", 1), 5
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                "TASK-2", "LOTTERY_REWARD", "DRAW-1", FulfillmentType.COUPON, 11L,
                new PointsFulfillmentPayload(100, "reward"), 5
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                "TASK-3", "LOTTERY_REWARD", "DRAW-1", FulfillmentType.POINTS, 0L,
                new PointsFulfillmentPayload(100, "reward"), 5
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                "TASK-4", "LOTTERY_REWARD", "DRAW-1", FulfillmentType.POINTS, 11L,
                new PointsFulfillmentPayload(100, "reward"), 101
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
