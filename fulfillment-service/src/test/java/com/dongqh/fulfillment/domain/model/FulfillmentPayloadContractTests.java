package com.dongqh.fulfillment.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentPayloadContractTests {

    @Test
    void validatesShippingPayloadAndRequiresMaskedPersonalInformation() {
        LogisticsFulfillmentPayload logistics = new LogisticsFulfillmentPayload(
                1, "SKU-1", 1, "王*", "137****9999", LogisticsFulfillmentPayload.REDACTED_REGION);

        assertThat(logistics.fulfillmentType()).isEqualTo(com.dongqh.fulfillment.enums.FulfillmentType.LOGISTICS);
        assertThatThrownBy(() -> new LogisticsFulfillmentPayload(
                1, "SKU-1", 1, "王五", "13712349999", "北京市海淀区"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPartiallyRedactedPersonalNamesAndLocationsAtThePayloadBoundary() {
        assertThatThrownBy(() -> new LogisticsFulfillmentPayload(
                1, "SKU-1", 1, "王五*", "137****9999", LogisticsFulfillmentPayload.REDACTED_REGION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LogisticsFulfillmentPayload(
                1, "SKU-1", 1, "王*", "137****9999", "北京市海淀区***"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
