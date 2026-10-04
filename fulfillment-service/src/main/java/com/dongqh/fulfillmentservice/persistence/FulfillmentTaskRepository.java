package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.model.FulfillmentTaskRecord;

import java.util.Optional;

public interface FulfillmentTaskRepository {

    Optional<FulfillmentTaskRecord> findByFulfillmentNo(String fulfillmentNo);

    FulfillmentTaskRecord insert(FulfillmentTaskRecord task);
}
