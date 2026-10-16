package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.domain.po.fulfillment.FulfillmentAttempt;
import com.dongqh.luckyhub.domain.po.fulfillment.FulfillmentQuarantine;
import com.dongqh.luckyhub.domain.po.fulfillment.FulfillmentTask;
import com.dongqh.luckyhub.enums.fulfillment.AttemptOperation;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentErrorCode;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentStatus;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
import com.dongqh.luckyhub.enums.fulfillment.GatewayOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentDomainContractTests {

    @Test
    void exposesStableFulfillmentTypesAndStates() {
        assertThat(FulfillmentType.values()).containsExactly(
                FulfillmentType.COUPON, FulfillmentType.POINTS,
                FulfillmentType.MEMBERSHIP, FulfillmentType.LOGISTICS);
        assertThat(FulfillmentStatus.values()).containsExactly(
                FulfillmentStatus.PENDING, FulfillmentStatus.PROCESSING,
                FulfillmentStatus.RETRY_WAITING, FulfillmentStatus.RECONCILING,
                FulfillmentStatus.SUCCEEDED, FulfillmentStatus.QUARANTINED,
                FulfillmentStatus.TERMINATED);
    }

    @Test
    void exposesWorkerOperationsOutcomesAndTenStableErrorCodes() {
        assertThat(AttemptOperation.values()).containsExactly(AttemptOperation.EXECUTE, AttemptOperation.QUERY);
        assertThat(GatewayOutcome.values()).containsExactly(
                GatewayOutcome.SUCCEEDED, GatewayOutcome.RETRYABLE_FAILURE,
                GatewayOutcome.PERMANENT_FAILURE, GatewayOutcome.UNKNOWN, GatewayOutcome.NOT_FOUND);
        assertThat(FulfillmentErrorCode.values()).hasSize(10);
    }

    @Test
    void providesPersistentAggregateTypes() {
        assertThat(FulfillmentTask.class).isNotNull();
        assertThat(FulfillmentAttempt.class).isNotNull();
        assertThat(FulfillmentQuarantine.class).isNotNull();
    }
}
