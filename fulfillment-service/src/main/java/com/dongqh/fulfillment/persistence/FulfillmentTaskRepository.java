package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.domain.model.FulfillmentTaskRecord;

import java.util.Optional;

public interface FulfillmentTaskRepository {

    Optional<FulfillmentTaskRecord> findByFulfillmentNo(String fulfillmentNo);

    FulfillmentTaskRecord insert(FulfillmentTaskRecord task);
}
