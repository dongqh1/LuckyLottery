package com.dongqh.luckyhub.commerce;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Phase3DomainContractTests {

    @Test
    void exposesStableDomainTypes() throws Exception {
        assertEnum("com.dongqh.luckyhub.enums.coupon.CouponType", "NO_THRESHOLD", "THRESHOLD");
        assertEnum("com.dongqh.luckyhub.enums.coupon.UserCouponStatus", "AVAILABLE", "LOCKED", "USED", "EXPIRED");
        assertEnum("com.dongqh.luckyhub.enums.membership.MembershipCardType", "MONTH", "QUARTER", "YEAR");
        assertEnum("com.dongqh.luckyhub.enums.order.CashOrderStatus", "PENDING_PAYMENT", "PAID", "CANCELLED");
        assertEnum("com.dongqh.luckyhub.enums.payment.PaymentStatus", "PENDING", "SUCCESS", "FAILED");
        assertEnum("com.dongqh.luckyhub.enums.payment.PaymentResult", "PROCESSING", "SUCCESS", "FAILURE");
        assertThat(Class.forName("com.dongqh.luckyhub.enums.coupon.CouponErrorCode").getEnumConstants()).hasSize(8);
        assertThat(Class.forName("com.dongqh.luckyhub.enums.membership.MembershipErrorCode").getEnumConstants()).hasSize(6);
        assertThat(Class.forName("com.dongqh.luckyhub.enums.order.OrderErrorCode").getEnumConstants()).hasSize(9);
        assertThat(Class.forName("com.dongqh.luckyhub.enums.payment.PaymentErrorCode").getEnumConstants()).hasSize(6);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertEnum(String className, String... values) throws Exception {
        Class<? extends Enum> type = (Class<? extends Enum>) Class.forName(className);
        assertThat(type.getEnumConstants()).extracting(Enum::name).containsExactly(values);
    }
}
