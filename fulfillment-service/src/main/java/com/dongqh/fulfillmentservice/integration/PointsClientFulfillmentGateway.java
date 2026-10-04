package com.dongqh.fulfillmentservice.integration;

import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.model.PointsFulfillmentPayload;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import com.dongqh.fulfillmentservice.service.port.PointsFulfillmentGateway;
import com.dongqh.hmapi.client.PointsClient;
import com.dongqh.hmapi.dto.PointsRewardCreditRequest;
import org.springframework.stereotype.Component;

@Component
public class PointsClientFulfillmentGateway implements PointsFulfillmentGateway {
    private final PointsClient points;
    private final FulfillmentTaskReadService tasks;

    public PointsClientFulfillmentGateway(PointsClient points, FulfillmentTaskReadService tasks) {
        this.points = points;
        this.tasks = tasks;
    }

    @Override
    public FulfillmentGatewayResult execute(String fulfillmentNo, long targetUserId,
                                            PointsFulfillmentPayload payload) {
        try {
            points.creditReward(new PointsRewardCreditRequest(fulfillmentNo, targetUserId,
                    payload.points(), payload.reason()));
            return FulfillmentGatewayResult.succeeded(fulfillmentNo);
        } catch (RuntimeException failure) {
            return FulfillmentFeignFailure.map(failure);
        }
    }

    @Override
    public FulfillmentGatewayResult query(String fulfillmentNo) {
        try {
            FulfillmentTaskView task = tasks.get(fulfillmentNo);
            if (!(task.payload() instanceof PointsFulfillmentPayload payload)) {
                return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "积分奖励快照无效");
            }
            return execute(fulfillmentNo, task.targetUserId(), payload);
        } catch (RuntimeException failure) {
            return FulfillmentFeignFailure.map(failure);
        }
    }
}
