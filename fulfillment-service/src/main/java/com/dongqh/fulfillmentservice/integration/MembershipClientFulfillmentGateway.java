package com.dongqh.fulfillmentservice.integration;

import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.model.MembershipFulfillmentPayload;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import com.dongqh.fulfillmentservice.service.port.MembershipFulfillmentGateway;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.MembershipRewardGrantRequest;
import org.springframework.stereotype.Component;

@Component
public class MembershipClientFulfillmentGateway implements MembershipFulfillmentGateway {
    private final MembershipClient memberships;
    private final FulfillmentTaskReadService tasks;

    public MembershipClientFulfillmentGateway(MembershipClient memberships, FulfillmentTaskReadService tasks) {
        this.memberships = memberships;
        this.tasks = tasks;
    }

    @Override
    public FulfillmentGatewayResult execute(String fulfillmentNo, long targetUserId,
                                            MembershipFulfillmentPayload payload) {
        try {
            memberships.grantReward(new MembershipRewardGrantRequest(fulfillmentNo, targetUserId,
                    payload.membershipProductId(), payload.membershipCode(), payload.durationDays(),
                    payload.quantity()));
            return FulfillmentGatewayResult.succeeded(fulfillmentNo);
        } catch (RuntimeException failure) {
            return FulfillmentFeignFailure.map(failure);
        }
    }

    @Override
    public FulfillmentGatewayResult query(String fulfillmentNo) {
        try {
            FulfillmentTaskView task = tasks.get(fulfillmentNo);
            if (!(task.payload() instanceof MembershipFulfillmentPayload payload)) {
                return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "会员奖励快照无效");
            }
            return execute(fulfillmentNo, task.targetUserId(), payload);
        } catch (RuntimeException failure) {
            return FulfillmentFeignFailure.map(failure);
        }
    }
}
