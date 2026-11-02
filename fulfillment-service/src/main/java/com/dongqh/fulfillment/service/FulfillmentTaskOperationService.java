package com.dongqh.fulfillment.service;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillment.persistence.FulfillmentTaskOperationRepository;
import com.dongqh.fulfillment.service.port.FulfillmentTaskReadyOutbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentTaskOperationService {

    private final FulfillmentTaskOperationRepository repository;
    private final FulfillmentTaskReadService tasks;
    private final FulfillmentTaskReadyOutbox outbox;

    public FulfillmentTaskOperationService(
            FulfillmentTaskOperationRepository repository,
            FulfillmentTaskReadService tasks,
            FulfillmentTaskReadyOutbox outbox
    ) {
        this.repository = repository;
        this.tasks = tasks;
        this.outbox = outbox;
    }

    @Transactional
    public FulfillmentTaskView retryQuarantined(String fulfillmentNo, long operatorId, String note) {
        String normalized = requiredFulfillmentNo(fulfillmentNo);
        requireOperator(operatorId);
        var task = repository.lockByFulfillmentNo(normalized)
                .orElseThrow(() -> new FulfillmentTaskNotFoundException(normalized));
        if (task.status() != FulfillmentStatus.QUARANTINED) {
            throw new FulfillmentTaskOperationNotAllowedException("RETRY", task.status());
        }
        if (repository.retryQuarantined(task.id()) != 1) {
            throw new FulfillmentTaskOperationConflictException(task.id());
        }
        repository.resolveQuarantine(task.id(), "RETRY", operatorId, safeNote(note));
        outbox.appendTaskReady(normalized);
        return tasks.get(normalized);
    }

    @Transactional
    public FulfillmentTaskView terminate(String fulfillmentNo, long operatorId, String note) {
        String normalized = requiredFulfillmentNo(fulfillmentNo);
        requireOperator(operatorId);
        var task = repository.lockByFulfillmentNo(normalized)
                .orElseThrow(() -> new FulfillmentTaskNotFoundException(normalized));
        if (task.status() == FulfillmentStatus.TERMINATED) {
            return tasks.get(normalized);
        }
        if (task.status() == FulfillmentStatus.SUCCEEDED || task.status() == FulfillmentStatus.PROCESSING) {
            throw new FulfillmentTaskOperationNotAllowedException("TERMINATE", task.status());
        }
        if (repository.terminate(task.id()) != 1) {
            throw new FulfillmentTaskOperationConflictException(task.id());
        }
        repository.resolveQuarantine(task.id(), "TERMINATED", operatorId, safeNote(note));
        return tasks.get(normalized);
    }

    private String requiredFulfillmentNo(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fulfillmentNo不能为空");
        }
        return value.trim();
    }

    private void requireOperator(long operatorId) {
        if (operatorId <= 0) {
            throw new IllegalArgumentException("operatorId必须大于0");
        }
    }

    private String safeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        return normalized.substring(0, Math.min(500, normalized.length()));
    }
}
