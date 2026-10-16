package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskDetails;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskOperationRepository;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskOperationSnapshot;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskQueryRepository;
import com.dongqh.fulfillmentservice.service.port.FulfillmentTaskReadyOutbox;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentTaskOperationServiceTests {

    @Test
    void retryingQuarantinedTaskClearsQuarantineAndAppendsReadyEvent() {
        MutableQueryRepository queryRepository = new MutableQueryRepository(
                details(FulfillmentStatus.QUARANTINED));
        FakeOperationRepository operations = new FakeOperationRepository(queryRepository,
                FulfillmentStatus.QUARANTINED);
        RecordingOutbox outbox = new RecordingOutbox();

        FulfillmentTaskView view = service(queryRepository, operations, outbox)
                .retryQuarantined(" TASK-1 ", 8, " operator note ");

        assertThat(view.status()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(operations.retryTaskId).isEqualTo(42L);
        assertThat(operations.resolution).isEqualTo("RETRY");
        assertThat(operations.operatorId).isEqualTo(8L);
        assertThat(operations.note).isEqualTo("operator note");
        assertThat(outbox.fulfillmentNumbers).containsExactly("TASK-1");
    }

    @Test
    void terminatingRetryableTaskClearsLeaseAndResolvesQuarantineWithoutOutbox() {
        MutableQueryRepository queryRepository = new MutableQueryRepository(
                details(FulfillmentStatus.RETRY_WAITING));
        FakeOperationRepository operations = new FakeOperationRepository(queryRepository,
                FulfillmentStatus.RETRY_WAITING);
        RecordingOutbox outbox = new RecordingOutbox();

        FulfillmentTaskView view = service(queryRepository, operations, outbox)
                .terminate("TASK-1", 9, "x".repeat(600));

        assertThat(view.status()).isEqualTo(FulfillmentStatus.TERMINATED);
        assertThat(operations.terminateTaskId).isEqualTo(42L);
        assertThat(operations.resolution).isEqualTo("TERMINATED");
        assertThat(operations.operatorId).isEqualTo(9L);
        assertThat(operations.note).hasSize(500);
        assertThat(outbox.fulfillmentNumbers).isEmpty();
    }

    @Test
    void refusesRetryAndTerminationForDisallowedStates() {
        for (FulfillmentStatus status : new FulfillmentStatus[]{
                FulfillmentStatus.PENDING,
                FulfillmentStatus.PROCESSING,
                FulfillmentStatus.SUCCEEDED,
                FulfillmentStatus.RETRY_WAITING,
                FulfillmentStatus.RECONCILING,
                FulfillmentStatus.TERMINATED}) {
            MutableQueryRepository queryRepository = new MutableQueryRepository(details(status));
            FakeOperationRepository operations = new FakeOperationRepository(queryRepository, status);
            RecordingOutbox outbox = new RecordingOutbox();
            FulfillmentTaskOperationService service = service(queryRepository, operations, outbox);

            assertThatThrownBy(() -> service.retryQuarantined("TASK-1", 10, null))
                    .isInstanceOf(FulfillmentTaskOperationNotAllowedException.class);
            assertThat(operations.retryTaskId).isNull();
            assertThat(outbox.fulfillmentNumbers).isEmpty();
        }

        for (FulfillmentStatus status : new FulfillmentStatus[]{
                FulfillmentStatus.PROCESSING, FulfillmentStatus.SUCCEEDED}) {
            MutableQueryRepository queryRepository = new MutableQueryRepository(details(status));
            FakeOperationRepository operations = new FakeOperationRepository(queryRepository, status);
            RecordingOutbox outbox = new RecordingOutbox();
            FulfillmentTaskOperationService service = service(queryRepository, operations, outbox);

            assertThatThrownBy(() -> service.terminate("TASK-1", 10, null))
                    .isInstanceOf(FulfillmentTaskOperationNotAllowedException.class);
            assertThat(operations.terminateTaskId).isNull();
            assertThat(outbox.fulfillmentNumbers).isEmpty();
        }
    }

    @Test
    void repeatedTerminationIsIdempotent() {
        MutableQueryRepository queryRepository = new MutableQueryRepository(
                details(FulfillmentStatus.TERMINATED));
        FakeOperationRepository operations = new FakeOperationRepository(queryRepository,
                FulfillmentStatus.TERMINATED);

        FulfillmentTaskView view = service(queryRepository, operations, new RecordingOutbox())
                .terminate("TASK-1", 11, null);

        assertThat(view.status()).isEqualTo(FulfillmentStatus.TERMINATED);
        assertThat(operations.terminateTaskId).isNull();
    }

    @Test
    void requiresExistingTaskAndPositiveOperatorId() {
        MutableQueryRepository queryRepository = new MutableQueryRepository(null);
        FakeOperationRepository operations = new FakeOperationRepository(queryRepository, null);
        FulfillmentTaskOperationService service = service(queryRepository, operations, new RecordingOutbox());

        assertThatThrownBy(() -> service.terminate("MISSING", 1, null))
                .isInstanceOf(FulfillmentTaskNotFoundException.class);
        queryRepository.details = details(FulfillmentStatus.RETRY_WAITING);
        operations.status = FulfillmentStatus.RETRY_WAITING;
        assertThatThrownBy(() -> service.terminate("TASK-1", 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FulfillmentTaskOperationService service(
            MutableQueryRepository queryRepository,
            FakeOperationRepository operations,
            RecordingOutbox outbox
    ) {
        return new FulfillmentTaskOperationService(
                operations,
                new FulfillmentTaskReadService(queryRepository, new ObjectMapper()),
                outbox
        );
    }

    private static FulfillmentTaskDetails details(FulfillmentStatus status) {
        return new FulfillmentTaskDetails(
                42L, "TASK-1", "LOTTERY_REWARD", "DRAW-1", FulfillmentType.COUPON, 17L,
                "{\"couponTemplateCode\":\"WELCOME\",\"quantity\":1}", "0".repeat(64), status,
                2, 5, null, null, null, null, null, null, null, null);
    }

    private static final class MutableQueryRepository implements FulfillmentTaskQueryRepository {
        private FulfillmentTaskDetails details;

        private MutableQueryRepository(FulfillmentTaskDetails details) {
            this.details = details;
        }

        @Override
        public Optional<FulfillmentTaskDetails> findByFulfillmentNo(String fulfillmentNo) {
            return details == null ? Optional.empty() : Optional.of(details);
        }

        @Override
        public java.util.List<com.dongqh.fulfillmentservice.persistence.FulfillmentTaskStatus> findLotteryStatusByFulfillmentNos(
                java.util.Collection<String> fulfillmentNos) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<FulfillmentTaskDetails> findPage(
                com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery query) {
            return details == null ? java.util.List.of() : java.util.List.of(details);
        }

        @Override
        public long count(com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery query) {
            return details == null ? 0 : 1;
        }
    }

    private static final class FakeOperationRepository implements FulfillmentTaskOperationRepository {
        private final MutableQueryRepository queryRepository;
        private FulfillmentStatus status;
        private Long retryTaskId;
        private Long terminateTaskId;
        private Long operatorId;
        private String resolution;
        private String note;

        private FakeOperationRepository(MutableQueryRepository queryRepository, FulfillmentStatus status) {
            this.queryRepository = queryRepository;
            this.status = status;
        }

        @Override
        public Optional<FulfillmentTaskOperationSnapshot> lockByFulfillmentNo(String fulfillmentNo) {
            if (queryRepository.details == null) {
                return Optional.empty();
            }
            return Optional.of(new FulfillmentTaskOperationSnapshot(
                    queryRepository.details.id(), status));
        }

        @Override
        public int retryQuarantined(long taskId) {
            retryTaskId = taskId;
            status = FulfillmentStatus.PENDING;
            queryRepository.details = details(status);
            return 1;
        }

        @Override
        public int terminate(long taskId) {
            terminateTaskId = taskId;
            status = FulfillmentStatus.TERMINATED;
            queryRepository.details = details(status);
            return 1;
        }

        @Override
        public void resolveQuarantine(long taskId, String resolution, long operatorId, String note) {
            this.resolution = resolution;
            this.operatorId = operatorId;
            this.note = note;
        }
    }

    private static final class RecordingOutbox implements FulfillmentTaskReadyOutbox {
        private final java.util.List<String> fulfillmentNumbers = new java.util.ArrayList<>();

        @Override
        public void appendTaskReady(String fulfillmentNo) {
            fulfillmentNumbers.add(fulfillmentNo);
        }
    }
}
