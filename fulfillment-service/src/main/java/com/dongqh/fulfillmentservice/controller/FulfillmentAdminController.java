package com.dongqh.fulfillmentservice.controller;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentPage;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskAdminQuery;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskRecord;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.MembershipFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.PointsFulfillmentPayload;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskCreationService;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskOperationService;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/fulfillment/tasks")
public class FulfillmentAdminController {
    private final FulfillmentTaskCreationService creation;
    private final FulfillmentTaskReadService reads;
    private final FulfillmentTaskOperationService operations;

    public FulfillmentAdminController(FulfillmentTaskCreationService creation,
                                      FulfillmentTaskReadService reads,
                                      FulfillmentTaskOperationService operations) {
        this.creation = creation;
        this.reads = reads;
        this.operations = operations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R<FulfillmentTaskView> create(@RequestBody CreateTaskRequest request) {
        if (request == null || request.fulfillmentType() == null || request.payload() == null) {
            throw new IllegalArgumentException("履约参数无效");
        }
        FulfillmentPayload payload = request.payload().toPayload(request.fulfillmentType());
        FulfillmentTaskRecord created = creation.create(new CreateFulfillmentTaskCommand(
                request.fulfillmentNo(), request.sourceType(), request.sourceId(), request.fulfillmentType(),
                request.targetUserId(), payload, request.maxAttempts()));
        return R.ok(reads.get(created.fulfillmentNo()));
    }

    @GetMapping
    public R<FulfillmentPage<FulfillmentTaskView>> page(@ModelAttribute FulfillmentTaskAdminQuery query) {
        return R.ok(reads.page(query.toQuery()));
    }

    @GetMapping("/{fulfillmentNo}")
    public R<FulfillmentTaskView> get(@PathVariable("fulfillmentNo") String fulfillmentNo) {
        return R.ok(reads.get(fulfillmentNo));
    }

    @PostMapping("/{fulfillmentNo}/retry")
    public R<FulfillmentTaskView> retry(@PathVariable("fulfillmentNo") String fulfillmentNo,
                                        @RequestBody(required = false) OperationRequest request) {
        return R.ok(operations.retryQuarantined(fulfillmentNo, requireOperatorId(), note(request)));
    }

    @PostMapping("/{fulfillmentNo}/terminate")
    public R<FulfillmentTaskView> terminate(@PathVariable("fulfillmentNo") String fulfillmentNo,
                                            @RequestBody(required = false) OperationRequest request) {
        return R.ok(operations.terminate(fulfillmentNo, requireOperatorId(), note(request)));
    }

    private long requireOperatorId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private String note(OperationRequest request) {
        if (request == null || request.note() == null) return null;
        String value = request.note().trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record OperationRequest(String note) { }

    public record CreateTaskRequest(String fulfillmentNo, String sourceType, String sourceId,
                                    FulfillmentType fulfillmentType, Long targetUserId,
                                    Payload payload, Integer maxAttempts) { }

    public record Payload(String couponTemplateCode, Integer quantity, Long points, String reason,
                          String membershipCode, Integer durationDays, Long shippingOrderId,
                          String skuCode, String receiverMasked, String phoneMasked, String regionMasked) {
        FulfillmentPayload toPayload(FulfillmentType type) {
            return switch (type) {
                case COUPON -> new CouponFulfillmentPayload(couponTemplateCode, required(quantity, "quantity"));
                case POINTS -> new PointsFulfillmentPayload(required(points, "points"), reason);
                case MEMBERSHIP -> new MembershipFulfillmentPayload(membershipCode,
                        required(durationDays, "durationDays"));
                case LOGISTICS -> new LogisticsFulfillmentPayload(required(shippingOrderId, "shippingOrderId"),
                        skuCode, required(quantity, "quantity"), receiverMasked, phoneMasked, regionMasked);
            };
        }

        private static int required(Integer value, String field) {
            if (value == null) throw new IllegalArgumentException(field + "不能为空");
            return value;
        }

        private static long required(Long value, String field) {
            if (value == null) throw new IllegalArgumentException(field + "不能为空");
            return value;
        }
    }
}

