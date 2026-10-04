package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.shipping.persistence.JdbcShippingOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcShippingOrderRepositoryTests {

    @Test
    void scansOnlyStatesThatCanStillChangeFromFulfillmentProjection() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(0L), eq(100))).thenReturn(List.of(41L));

        List<Long> ids = new JdbcShippingOrderRepository(jdbc).selectProjectionCandidateIdsAfter(0, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(Long.class), eq(0L), eq(100));
        String normalized = sql.getValue().replaceAll("\\s+", " ");
        assertThat(ids).containsExactly(41L);
        assertThat(normalized)
                .contains("so.id > ?")
                .contains("ORDER BY so.id ASC")
                .contains("ft.status='SUCCEEDED' AND so.status IN ('READY','FULFILLING','FAILED')")
                .contains("ft.status='QUARANTINED' AND so.status='FULFILLING'")
                .contains("ft.status='TERMINATED' AND so.status IN ('READY','FULFILLING','FAILED')")
                .contains("ft.status IN ('PENDING','RETRY_WAITING','PROCESSING','RECONCILING') AND so.status='FAILED'")
                .doesNotContain("so.status NOT IN ('DELIVERED','TERMINATED')");
    }

    @Test
    void wrapsUsingTheSameShippingOrderIdThatTheQueryReturns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(500L), eq(99))).thenReturn(List.of(73L));

        List<Long> ids = new JdbcShippingOrderRepository(jdbc).selectProjectionCandidateIdsBefore(500, 99);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(Long.class), eq(500L), eq(99));
        String normalized = sql.getValue().replaceAll("\\s+", " ");
        assertThat(ids).containsExactly(73L);
        assertThat(normalized).contains("so.id <= ?").contains("ORDER BY so.id ASC");
    }

    @Test
    void fairnessPageUsesAnInclusiveHighWaterAndExclusiveCursor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(73L), eq(913L), eq(100)))
                .thenReturn(List.of(91L));

        List<Long> ids = new JdbcShippingOrderRepository(jdbc)
                .selectProjectionCandidateIdsBetween(73, 913, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(Long.class), eq(73L), eq(913L), eq(100));
        String normalized = sql.getValue().replaceAll("\\s+", " ");
        assertThat(ids).containsExactly(91L);
        assertThat(normalized)
                .contains("so.id > ? AND so.id <= ?")
                .contains("ORDER BY so.id ASC");
    }

    @Test
    void capturesTheCurrentMaximumEligibleShippingOrderId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(913L);

        long highWaterMark = new JdbcShippingOrderRepository(jdbc).selectProjectionCandidateHighWaterMark();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class));
        assertThat(highWaterMark).isEqualTo(913L);
        assertThat(sql.getValue())
                .contains("MAX(so.id)")
                .contains("JOIN shipping_order so ON so.fulfillment_no=ft.fulfillment_no")
                .contains("ft.status='SUCCEEDED'");
    }
}
