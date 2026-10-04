package com.dongqh.fulfillmentservice.integration;

import com.dongqh.fulfillmentservice.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import com.dongqh.fulfillmentservice.service.port.CouponFulfillmentGateway;
import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.dto.CouponRewardIssueRequest;
import org.springframework.stereotype.Component;

@Component
public class CouponClientFulfillmentGateway implements CouponFulfillmentGateway {
    private final CouponClient coupons;
    private final FulfillmentTaskReadService tasks;

    public CouponClientFulfillmentGateway(CouponClient coupons, FulfillmentTaskReadService tasks) {
        this.coupons = coupons;
        this.tasks = tasks;
    }

    @Override
    public FulfillmentGatewayResult execute(String fulfillmentNo, long targetUserId,
                                            CouponFulfillmentPayload payload) {
        try {
            coupons.issueReward(new CouponRewardIssueRequest(fulfillmentNo, targetUserId,
                    payload.couponTemplateId(), payload.couponTemplateCode(), payload.quantity()));
            return FulfillmentGatewayResult.succeeded(fulfillmentNo);
        } catch (RuntimeException failure) {
            return FulfillmentFeignFailure.map(failure);
        }
    }

    @Override
    public FulfillmentGatewayResult query(String fulfillmentNo) {
        try {
            FulfillmentTaskView task = tasks.get(fulfillmentNo);
            if (!(task.payload() instanceof CouponFulfillmentPayload payload)) {
                return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "优惠券奖励快照无效");
            }
            // Providers use deterministic business keys, so replaying the same request resolves unknown results safely.
            return execute(fulfillmentNo, task.targetUserId(), payload);
        } catch (RuntimeException failure) {
            return FulfillmentFeignFailure.map(failure);
        }
    }
}
