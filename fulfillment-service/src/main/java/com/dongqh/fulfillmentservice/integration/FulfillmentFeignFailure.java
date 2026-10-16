package com.dongqh.fulfillmentservice.integration;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentGatewayResult;
import feign.FeignException;

final class FulfillmentFeignFailure {
    private FulfillmentFeignFailure() {
    }

    static FulfillmentGatewayResult map(RuntimeException failure) {
        if (!(failure instanceof FeignException feign)) {
            return FulfillmentGatewayResult.unknown("PROVIDER_UNAVAILABLE", "下游服务结果未知");
        }
        int status = feign.status();
        if (status == 409 || status == 408 || status == 425 || status == 429) {
            return FulfillmentGatewayResult.retryable("PROVIDER_RETRYABLE", "下游服务暂不可处理");
        }
        if (status >= 400 && status < 500) {
            return FulfillmentGatewayResult.permanent("PROVIDER_REJECTED", "下游服务拒绝履约请求");
        }
        return FulfillmentGatewayResult.unknown("PROVIDER_UNAVAILABLE", "下游服务结果未知");
    }
}
