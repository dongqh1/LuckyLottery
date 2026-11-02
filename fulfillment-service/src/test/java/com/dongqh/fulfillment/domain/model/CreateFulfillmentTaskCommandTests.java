package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.domain.model.LogisticsFulfillmentPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateFulfillmentTaskCommandTests {

    @Test
    void trimsIdentityFieldsAndDefaultsAttemptLimit() {
        CreateFulfillmentTaskCommand command = new CreateFulfillmentTaskCommand(
                " TASK-1 ",
                " LOTTERY_BENEFIT ",
                " DRAW-1 ",
                FulfillmentType.LOGISTICS,
                11L,
                payload(),
                null
        );

        assertThat(command.fulfillmentNo()).isEqualTo("TASK-1");
        assertThat(command.sourceType()).isEqualTo("LOTTERY_BENEFIT");
        assertThat(command.sourceId()).isEqualTo("DRAW-1");
        assertThat(command.maxAttempts()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidIdentityTypeUserAndAttemptLimit() {
        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                " ", "LOTTERY_BENEFIT", "DRAW-1", FulfillmentType.LOGISTICS, 11L,
                payload(), 5
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                "TASK-2", "LOTTERY_BENEFIT", "DRAW-1", FulfillmentType.COUPON, 11L,
                payload(), 5
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                "TASK-3", "LOTTERY_BENEFIT", "DRAW-1", FulfillmentType.LOGISTICS, 0L,
                payload(), 5
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CreateFulfillmentTaskCommand(
                "TASK-4", "LOTTERY_BENEFIT", "DRAW-1", FulfillmentType.LOGISTICS, 11L,
                payload(), 101
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static LogisticsFulfillmentPayload payload() {
        return new LogisticsFulfillmentPayload(8, "SKU-1", 1, "王*", "138****8000",
                LogisticsFulfillmentPayload.REDACTED_REGION);
    }
}
