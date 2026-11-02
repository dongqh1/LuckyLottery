package com.dongqh.fulfillment.service;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.domain.model.FulfillmentTaskQuery;
import com.dongqh.fulfillment.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillment.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillment.persistence.FulfillmentTaskDetails;
import com.dongqh.fulfillment.persistence.FulfillmentTaskQueryRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentTaskReadServiceTests {

    @Test
    void getMapsStoredSnapshotToTypedPayload() {
        FakeRepository repository = new FakeRepository();
        repository.byNo = Optional.of(details("TASK-1", payloadJson(2)));

        FulfillmentTaskView view = service(repository).get(" TASK-1 ");

        assertThat(view.fulfillmentNo()).isEqualTo("TASK-1");
        assertThat(view.payload()).isEqualTo(payload(2));
        assertThat(view.status()).isEqualTo(FulfillmentStatus.RETRY_WAITING);
    }

    @Test
    void pageReturnsTypedRecordsAndPaginationMetadata() {
        FakeRepository repository = new FakeRepository();
        repository.page = List.of(details("TASK-2", payloadJson(1)));
        repository.total = 41;
        FulfillmentTaskQuery query = new FulfillmentTaskQuery(
                3, 20, FulfillmentType.LOGISTICS, FulfillmentStatus.RETRY_WAITING, 17L, "LOTTERY_BENEFIT", "DRAW-7");

        var result = service(repository).page(query);

        assertThat(repository.lastQuery).isEqualTo(query);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).payload()).isEqualTo(payload(1));
        assertThat(result.total()).isEqualTo(41);
        assertThat(result.current()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.pages()).isEqualTo(3);
    }

    @Test
    void missingOrMalformedTaskSnapshotProducesDomainErrors() {
        FakeRepository repository = new FakeRepository();
        FulfillmentTaskReadService service = service(repository);

        assertThatThrownBy(() -> service.get("MISSING"))
                .isInstanceOf(FulfillmentTaskNotFoundException.class);

        repository.byNo = Optional.of(details("BROKEN", "{not-json"));
        assertThatThrownBy(() -> service.get("BROKEN"))
                .isInstanceOf(FulfillmentTaskInvalidPayloadException.class);
    }

    @Test
    void queryRejectsInvalidPaginationAndUserId() {
        assertThatThrownBy(() -> new FulfillmentTaskQuery(
                0, 20, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FulfillmentTaskQuery(
                1, 101, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FulfillmentTaskQuery(
                1, 20, null, null, 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FulfillmentTaskReadService service(FakeRepository repository) {
        return new FulfillmentTaskReadService(repository, new ObjectMapper());
    }

    private static FulfillmentTaskDetails details(String no, String payload) {
        return new FulfillmentTaskDetails(
                1L, no, "LOTTERY_BENEFIT", "DRAW-1", FulfillmentType.LOGISTICS, 17L, payload,
                "0".repeat(64), FulfillmentStatus.RETRY_WAITING, 2, 5, null, "EXT-1", null,
                "TEMP", "retry later", null,
                LocalDateTime.of(2026, 9, 27, 4, 0), LocalDateTime.of(2026, 9, 27, 4, 1));
    }

    private static LogisticsFulfillmentPayload payload(int quantity) {
        return new LogisticsFulfillmentPayload(8, "SKU-1", quantity, "王*", "138****8000",
                LogisticsFulfillmentPayload.REDACTED_REGION);
    }

    private static String payloadJson(int quantity) {
        return "{\"shippingOrderId\":8,\"skuCode\":\"SKU-1\",\"quantity\":" + quantity
                + ",\"receiverMasked\":\"王*\",\"phoneMasked\":\"138****8000\",\"regionMasked\":\""
                + LogisticsFulfillmentPayload.REDACTED_REGION + "\"}";
    }

    private static final class FakeRepository implements FulfillmentTaskQueryRepository {
        private Optional<FulfillmentTaskDetails> byNo = Optional.empty();
        private List<FulfillmentTaskDetails> page = List.of();
        private long total;
        private FulfillmentTaskQuery lastQuery;

        @Override
        public Optional<FulfillmentTaskDetails> findByFulfillmentNo(String fulfillmentNo) {
            return byNo;
        }

        @Override
        public List<FulfillmentTaskDetails> findPage(FulfillmentTaskQuery query) {
            lastQuery = query;
            return page;
        }

        @Override
        public long count(FulfillmentTaskQuery query) {
            lastQuery = query;
            return total;
        }
    }
}
