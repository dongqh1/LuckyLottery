package com.dongqh.fulfillment.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcFulfillmentTaskStateRepositoryTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JdbcFulfillmentTaskStateRepository repository =
            new JdbcFulfillmentTaskStateRepository(jdbc);

    @Test
    void regularDueScanDoesNotCompeteWithRabbitForNewPendingTasks() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        repository.lockDue(LocalDateTime.of(2026, 9, 27, 12, 0), 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("fulfillment_type='LOGISTICS'");
        assertThat(sql.getValue()).contains("status IN ('RETRY_WAITING','RECONCILING')");
        assertThat(sql.getValue()).doesNotContain("'PENDING'");
    }

    @Test
    void compensationScanOnlyClaimsPendingTasksOlderThanItsGraceBoundary() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        repository.lockCompensationDue(
                LocalDateTime.of(2026, 9, 27, 12, 0),
                LocalDateTime.of(2026, 9, 27, 11, 59, 30),
                100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("fulfillment_type='LOGISTICS'");
        assertThat(sql.getValue()).contains("status='PENDING'").contains("created_at<=?");
        assertThat(sql.getValue()).doesNotContain("'RETRY_WAITING'").doesNotContain("'RECONCILING'");
    }
}
