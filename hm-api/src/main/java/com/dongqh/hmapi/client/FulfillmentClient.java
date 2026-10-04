package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.CreateFulfillmentShippingOrderRequest;
import com.dongqh.hmapi.dto.FulfillmentShippingOrderDTO;
import com.dongqh.hmapi.dto.CreateFulfillmentTaskRequest;
import com.dongqh.hmapi.dto.FulfillmentTaskDTO;
import com.dongqh.hmapi.dto.FulfillmentTaskStatusDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient("fulfillment-service")
public interface FulfillmentClient {

    @PostMapping("/internal/tasks")
    FulfillmentTaskDTO createTask(@RequestBody CreateFulfillmentTaskRequest request);

    @GetMapping("/internal/tasks/lottery-status")
    List<FulfillmentTaskStatusDTO> getLotteryTaskStatuses(
            @RequestParam("fulfillmentNos") Collection<String> fulfillmentNos
    );

    @PostMapping("/internal/shipping/orders")
    FulfillmentShippingOrderDTO createShippingOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody CreateFulfillmentShippingOrderRequest request
    );

    @GetMapping("/internal/shipping/orders")
    List<FulfillmentShippingOrderDTO> getShippingOrdersByIds(
            @RequestParam("ids") Collection<Long> ids
    );
}
