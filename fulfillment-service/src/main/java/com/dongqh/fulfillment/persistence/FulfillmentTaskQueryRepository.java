package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.domain.model.FulfillmentTaskQuery;

import java.util.List;
import java.util.Optional;

public interface FulfillmentTaskQueryRepository {

    Optional<FulfillmentTaskDetails> findByFulfillmentNo(String fulfillmentNo);

    List<FulfillmentTaskDetails> findPage(FulfillmentTaskQuery query);

    long count(FulfillmentTaskQuery query);
}
