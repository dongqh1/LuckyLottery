package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentPage;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskDetails;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskQueryRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class FulfillmentTaskReadService {

    private final FulfillmentTaskQueryRepository repository;
    private final ObjectMapper json;

    public FulfillmentTaskReadService(FulfillmentTaskQueryRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public FulfillmentTaskView get(String fulfillmentNo) {
        String normalized = required(fulfillmentNo);
        FulfillmentTaskDetails details = repository.findByFulfillmentNo(normalized)
                .orElseThrow(() -> new FulfillmentTaskNotFoundException(normalized));
        return view(details);
    }

    public FulfillmentPage<FulfillmentTaskView> page(FulfillmentTaskQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("履约任务查询条件不能为空");
        }
        long total = repository.count(query);
        List<FulfillmentTaskView> records = repository.findPage(query).stream().map(this::view).toList();
        long pages = total / query.size() + (total % query.size() == 0 ? 0 : 1);
        return new FulfillmentPage<>(records, total, query.page(), query.size(), pages);
    }

    private FulfillmentTaskView view(FulfillmentTaskDetails details) {
        return new FulfillmentTaskView(
                details.id(),
                details.fulfillmentNo(),
                details.sourceType(),
                details.sourceId(),
                details.fulfillmentType(),
                details.targetUserId(),
                payload(details),
                details.requestFingerprint(),
                details.status(),
                details.attemptCount(),
                details.maxAttempts(),
                details.nextAttemptAt(),
                details.externalReference(),
                details.lastErrorCategory(),
                details.lastErrorCode(),
                details.lastErrorMessage(),
                details.completedAt(),
                details.createdAt(),
                details.updatedAt()
        );
    }

    private FulfillmentPayload payload(FulfillmentTaskDetails details) {
        if (details.fulfillmentType() != com.dongqh.fulfillmentservice.enums.FulfillmentType.LOGISTICS) {
            throw new FulfillmentTaskInvalidPayloadException(details.fulfillmentNo(),
                    new IllegalArgumentException("物流服务不读取历史虚拟奖励任务"));
        }
        try {
            return json.readValue(details.requestPayload(), LogisticsFulfillmentPayload.class);
        } catch (JacksonException exception) {
            throw new FulfillmentTaskInvalidPayloadException(details.fulfillmentNo(), exception);
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fulfillmentNo不能为空");
        }
        return value.trim();
    }
}
