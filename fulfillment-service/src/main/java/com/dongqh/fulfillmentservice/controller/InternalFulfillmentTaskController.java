package com.dongqh.fulfillmentservice.controller;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillmentservice.model.FulfillmentPayload;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskRecord;
import com.dongqh.fulfillmentservice.model.MembershipFulfillmentPayload;
import com.dongqh.fulfillmentservice.model.PointsFulfillmentPayload;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskCreationService;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskIdempotencyConflictException;
import com.dongqh.hmapi.dto.CreateFulfillmentTaskRequest;
import com.dongqh.hmapi.dto.FulfillmentCouponTaskPayload;
import com.dongqh.hmapi.dto.FulfillmentMembershipTaskPayload;
import com.dongqh.hmapi.dto.FulfillmentPointsTaskPayload;
import com.dongqh.hmapi.dto.FulfillmentTaskDTO;
import com.dongqh.hmapi.dto.FulfillmentTaskStatusDTO;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/internal/tasks")
public class InternalFulfillmentTaskController {
    private static final String LOTTERY_SOURCE = "LOTTERY_BENEFIT";

    private final FulfillmentTaskCreationService tasks;
    private final FulfillmentTaskReadService taskReads;
    private final ObjectMapper json;

    public InternalFulfillmentTaskController(FulfillmentTaskCreationService tasks,
                                             FulfillmentTaskReadService taskReads, ObjectMapper json) {
        this.tasks = tasks;
        this.taskReads = taskReads;
        this.json = json;
    }

    @GetMapping("/lottery-status")
    public List<FulfillmentTaskStatusDTO> getLotteryStatuses(
            @RequestParam("fulfillmentNos") Collection<String> fulfillmentNos) {
        return taskReads.getLotteryStatuses(fulfillmentNos).stream()
                .map(status -> new FulfillmentTaskStatusDTO(status.fulfillmentNo(), status.status().name()))
                .toList();
    }

    @PostMapping
    public FulfillmentTaskDTO create(@RequestBody CreateFulfillmentTaskRequest request) {
        if (request == null || !LOTTERY_SOURCE.equals(request.sourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的履约来源");
        }
        FulfillmentType type;
        try {
            type = FulfillmentType.valueOf(request.fulfillmentType());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "履约类型无效");
        }
        FulfillmentPayload payload = payload(type, request.payloadJson());
        FulfillmentTaskRecord created = tasks.create(new CreateFulfillmentTaskCommand(
                request.fulfillmentNo(), request.sourceType(), request.sourceId(), type,
                request.targetUserId(), payload, request.maxAttempts()));
        return new FulfillmentTaskDTO(created.id(), created.fulfillmentNo(), created.sourceType(),
                created.sourceId(), created.fulfillmentType().name(), created.targetUserId(),
                created.requestFingerprint(), created.status().name());
    }

    @ExceptionHandler(FulfillmentTaskIdempotencyConflictException.class)
    public ResponseEntity<Void> handleIdempotencyConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    private FulfillmentPayload payload(FulfillmentType type, String payloadJson) {
        try {
            return switch (type) {
                case COUPON -> {
                    FulfillmentCouponTaskPayload value = json.readValue(payloadJson, FulfillmentCouponTaskPayload.class);
                    yield new CouponFulfillmentPayload(value.couponTemplateCode(), value.quantity(),
                            value.couponTemplateId());
                }
                case POINTS -> {
                    FulfillmentPointsTaskPayload value = json.readValue(payloadJson, FulfillmentPointsTaskPayload.class);
                    yield new PointsFulfillmentPayload(value.points(), value.reason());
                }
                case MEMBERSHIP -> {
                    FulfillmentMembershipTaskPayload value = json.readValue(payloadJson, FulfillmentMembershipTaskPayload.class);
                    yield new MembershipFulfillmentPayload(value.membershipCode(), value.durationDays(),
                            value.quantity(), value.membershipProductId());
                }
                case LOGISTICS -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "物流任务不支持此入口");
            };
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "履约参数无效");
        }
    }
}
