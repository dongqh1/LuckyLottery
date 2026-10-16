package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface FulfillmentTaskQueryRepository {

    Optional<FulfillmentTaskDetails> findByFulfillmentNo(String fulfillmentNo);

    List<FulfillmentTaskStatus> findLotteryStatusByFulfillmentNos(Collection<String> fulfillmentNos);

    List<FulfillmentTaskDetails> findPage(FulfillmentTaskQuery query);

    long count(FulfillmentTaskQuery query);
}
