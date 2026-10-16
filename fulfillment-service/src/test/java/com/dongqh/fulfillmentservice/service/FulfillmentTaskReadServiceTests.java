package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskDetails;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskQueryRepository;
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
        repository.byNo = Optional.of(details("TASK-1", "{\"couponTemplateCode\":\"WELCOME\",\"quantity\":2}"));

        FulfillmentTaskView view = service(repository).get(" TASK-1 ");

        assertThat(view.fulfillmentNo()).isEqualTo("TASK-1");
        assertThat(view.payload()).isEqualTo(new CouponFulfillmentPayload("WELCOME", 2));
        assertThat(view.status()).isEqualTo(FulfillmentStatus.RETRY_WAITING);
    }

    @Test
    void pageReturnsTypedRecordsAndPaginationMetadata() {
        FakeRepository repository = new FakeRepository();
        repository.page = List.of(details("TASK-2", "{\"couponTemplateCode\":\"WELCOME\",\"quantity\":1}"));
        repository.total = 41;
        FulfillmentTaskQuery query = new FulfillmentTaskQuery(
                3, 20, FulfillmentType.COUPON, FulfillmentStatus.RETRY_WAITING, 17L, "LOTTERY", "DRAW-7");

        var result = service(repository).page(query);

        assertThat(repository.lastQuery).isEqualTo(query);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).payload()).isEqualTo(new CouponFulfillmentPayload("WELCOME", 1));
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
                1L, no, "LOTTERY_REWARD", "DRAW-1", FulfillmentType.COUPON, 17L, payload,
                "0".repeat(64), FulfillmentStatus.RETRY_WAITING, 2, 5, null, "EXT-1", null,
                "TEMP", "retry later", null,
                LocalDateTime.of(2026, 9, 27, 4, 0), LocalDateTime.of(2026, 9, 27, 4, 1));
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
        public List<com.dongqh.fulfillmentservice.persistence.FulfillmentTaskStatus> findLotteryStatusByFulfillmentNos(
                java.util.Collection<String> fulfillmentNos) {
            return List.of();
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
