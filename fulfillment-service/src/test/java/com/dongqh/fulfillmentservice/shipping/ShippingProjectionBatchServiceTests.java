package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillmentservice.service.shipping.ShippingOrderProjectionWorker;
import com.dongqh.fulfillmentservice.service.shipping.ShippingProjectionBatchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingProjectionBatchServiceTests {

    @Test
    void projectsEachCandidateIndependentlyAndContinuesAfterOneFailure() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrderProjectionWorker worker = mock(ShippingOrderProjectionWorker.class);
        when(orders.selectProjectionCandidateIdsAfter(0, 100)).thenReturn(List.of(7L, 8L, 9L));
        when(worker.projectOne(7L)).thenReturn(true);
        when(worker.projectOne(8L)).thenThrow(new IllegalStateException("safe test failure"));
        when(worker.projectOne(9L)).thenReturn(false);

        int completed = new ShippingProjectionBatchService(orders, worker).projectPending();

        assertThat(completed).isEqualTo(1);
        verify(orders).selectProjectionCandidateIdsAfter(0, 100);
        verify(worker).projectOne(7L);
        verify(worker).projectOne(8L);
        verify(worker).projectOne(9L);
    }

    @Test
    void rotatesPastPersistentCandidatesAndWrapsToTheBeginning() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrderProjectionWorker worker = mock(ShippingOrderProjectionWorker.class);
        List<Long> firstPage = LongStream.rangeClosed(1, 100).boxed().toList();
        when(orders.selectProjectionCandidateIdsAfter(0, 100)).thenReturn(firstPage);
        when(orders.selectProjectionCandidateIdsAfter(100, 100)).thenReturn(List.of(101L));
        when(orders.selectProjectionCandidateIdsBefore(100, 99)).thenReturn(List.of(1L));

        ShippingProjectionBatchService service = new ShippingProjectionBatchService(orders, worker);
        service.projectPending();
        service.projectPending();

        verify(worker).projectOne(101L);
        verify(orders).selectProjectionCandidateIdsAfter(100, 100);
        verify(orders).selectProjectionCandidateIdsBefore(100, 99);
    }

    @Test
    void periodicallyScansToAFixedHighWaterWithoutRewindingForwardProgress() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrderProjectionWorker worker = mock(ShippingOrderProjectionWorker.class);
        when(orders.selectProjectionCandidateIdsAfter(anyLong(), eq(100)))
                .thenAnswer(invocation -> {
                    long cursor = invocation.getArgument(0);
                    return LongStream.rangeClosed(cursor + 1, cursor + 100).boxed().toList();
                });
        when(orders.selectProjectionCandidateHighWaterMark()).thenReturn(2000L);
        when(orders.selectProjectionCandidateIdsBetween(0, 2000, 100))
                .thenReturn(LongStream.rangeClosed(1, 100).boxed().toList());
        ShippingProjectionBatchService service = new ShippingProjectionBatchService(orders, worker);

        for (int scan = 0; scan < 12; scan++) {
            service.projectPending();
        }

        verify(orders).selectProjectionCandidateHighWaterMark();
        verify(orders).selectProjectionCandidateIdsBetween(0, 2000, 100);
        verify(orders).selectProjectionCandidateIdsAfter(0, 100);
        verify(orders).selectProjectionCandidateIdsAfter(1000, 100);
    }

    @Test
    void fairnessScanStillRunsWhenAFullBatchIs99ForwardRowsPlusOneWrappedRow() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrderProjectionWorker worker = mock(ShippingOrderProjectionWorker.class);
        when(orders.selectProjectionCandidateIdsAfter(anyLong(), eq(100)))
                .thenAnswer(invocation -> {
                    long cursor = invocation.getArgument(0);
                    return LongStream.rangeClosed(cursor + 1, cursor + 99).boxed().toList();
                });
        when(orders.selectProjectionCandidateIdsBefore(anyLong(), eq(1))).thenReturn(List.of(1L));
        when(orders.selectProjectionCandidateHighWaterMark()).thenReturn(5000L);
        when(orders.selectProjectionCandidateIdsBetween(0, 5000, 100))
                .thenReturn(LongStream.rangeClosed(1, 100).boxed().toList());
        ShippingProjectionBatchService service = new ShippingProjectionBatchService(orders, worker);

        for (int scan = 0; scan < 10; scan++) {
            service.projectPending();
        }

        verify(orders).selectProjectionCandidateHighWaterMark();
        verify(orders).selectProjectionCandidateIdsBetween(0, 5000, 100);
    }
}
