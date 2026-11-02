package com.dongqh.fulfillment.persistence;

import java.util.Optional;

public interface FulfillmentTaskOperationRepository {

    Optional<FulfillmentTaskOperationSnapshot> lockByFulfillmentNo(String fulfillmentNo);

    int retryQuarantined(long taskId);

    int terminate(long taskId);

    void resolveQuarantine(long taskId, String resolution, long operatorId, String note);
}
