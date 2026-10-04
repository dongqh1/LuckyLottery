package com.dongqh.fulfillmentservice.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentPayloadContractTests {

    @Test
    void preservesPayloadTypesAndNormalizesRequiredValues() {
        CouponFulfillmentPayload coupon = new CouponFulfillmentPayload("  SUMMER  ", 2);
        PointsFulfillmentPayload points = new PointsFulfillmentPayload(500, "  draw reward  ");
        MembershipFulfillmentPayload membership = new MembershipFulfillmentPayload("  VIP  ", 30);

        assertThat(coupon.couponTemplateCode()).isEqualTo("SUMMER");
        assertThat(coupon.fulfillmentType()).isEqualTo(FulfillmentType.COUPON);
        assertThat(points.reason()).isEqualTo("draw reward");
        assertThat(points.fulfillmentType()).isEqualTo(FulfillmentType.POINTS);
        assertThat(membership.membershipCode()).isEqualTo("VIP");
        assertThat(membership.fulfillmentType()).isEqualTo(FulfillmentType.MEMBERSHIP);
    }

    @Test
    void rejectsInvalidCountsAndEmptyIdentifiers() {
        assertThatThrownBy(() -> new CouponFulfillmentPayload("  ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CouponFulfillmentPayload("C", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PointsFulfillmentPayload(0, "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MembershipFulfillmentPayload("VIP", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresMaskedLogisticsFields() {
        LogisticsFulfillmentPayload logistics = new LogisticsFulfillmentPayload(
                1, "SKU-1", 1, "王*", "137****9999", LogisticsFulfillmentPayload.REDACTED_REGION);

        assertThat(logistics.fulfillmentType()).isEqualTo(FulfillmentType.LOGISTICS);
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
