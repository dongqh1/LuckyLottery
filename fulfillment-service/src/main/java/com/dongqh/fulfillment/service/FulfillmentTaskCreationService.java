package com.dongqh.fulfillment.service;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.domain.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillment.domain.model.FulfillmentTaskRecord;
import com.dongqh.fulfillment.persistence.DuplicateFulfillmentTaskException;
import com.dongqh.fulfillment.persistence.FulfillmentTaskRepository;
import com.dongqh.fulfillment.service.port.FulfillmentTaskReadyOutbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class FulfillmentTaskCreationService {

    private final FulfillmentTaskRepository repository;
    private final FulfillmentTaskReadyOutbox outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public FulfillmentTaskCreationService(
            FulfillmentTaskRepository repository,
            FulfillmentTaskReadyOutbox outbox,
            ObjectMapper json,
            Clock fulfillmentClock
    ) {
        this.repository = repository;
        this.outbox = outbox;
        this.json = json;
        this.clock = fulfillmentClock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FulfillmentTaskRecord create(CreateFulfillmentTaskCommand command) {
        if (command.fulfillmentType() != com.dongqh.fulfillment.enums.FulfillmentType.LOGISTICS) {
            throw new IllegalArgumentException("物流服务只接受实物发货任务");
        }
        String payload = serialize(command);
        String fingerprint = fingerprint(command, payload);
        var existing = repository.findByFulfillmentNo(command.fulfillmentNo());
        if (existing.isPresent()) {
            return requireSameRequest(existing.get(), fingerprint);
        }

        FulfillmentTaskRecord candidate = new FulfillmentTaskRecord(
                null,
                command.fulfillmentNo(),
                command.sourceType(),
                command.sourceId(),
                command.fulfillmentType(),
                command.targetUserId(),
                payload,
                fingerprint,
                FulfillmentStatus.PENDING,
                0,
                command.maxAttempts(),
                LocalDateTime.now(clock)
        );

        FulfillmentTaskRecord created;
        try {
            created = repository.insert(candidate);
        } catch (DuplicateFulfillmentTaskException race) {
            return repository.findByFulfillmentNo(command.fulfillmentNo())
                    .map(task -> requireSameRequest(task, fingerprint))
                    .orElseThrow(() -> race);
        }

        outbox.appendTaskReady(created.fulfillmentNo());
        return created;
    }

    private FulfillmentTaskRecord requireSameRequest(FulfillmentTaskRecord task, String fingerprint) {
        if (!Objects.equals(task.requestFingerprint(), fingerprint)) {
            throw new FulfillmentTaskIdempotencyConflictException(task.fulfillmentNo());
        }
        return task;
    }

    private String serialize(CreateFulfillmentTaskCommand command) {
        try {
            return json.writeValueAsString(command.payload());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("履约参数序列化失败", exception);
        }
    }

    private String fingerprint(CreateFulfillmentTaskCommand command, String payload) {
        String identity = command.sourceType() + "\n"
                + command.sourceId() + "\n"
                + command.fulfillmentType() + "\n"
                + command.targetUserId() + "\n"
                + command.maxAttempts() + "\n"
                + payload;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }
}
