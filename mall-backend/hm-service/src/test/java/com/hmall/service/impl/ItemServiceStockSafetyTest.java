package com.hmall.service.impl;

import com.hmall.common.exception.BizIllegalException;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.mapper.ItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceStockSafetyTest {

    @Test
    void rejectsInvalidQuantityBeforeUpdating() {
        ItemMapper mapper = mock(ItemMapper.class);
        ItemServiceImpl service = service(mapper);

        assertThatThrownBy(() -> service.deductStock(List.of(detail(1L, 0))))
                .isInstanceOf(BizIllegalException.class);
    }

    @Test
    void rejectsWhenAtomicUpdateAffectsNoRows() {
        ItemMapper mapper = mock(ItemMapper.class);
        ItemServiceImpl service = service(mapper);
        OrderDetailDTO detail = detail(1L, 2);
        when(mapper.updateStock(detail)).thenReturn(0);

        assertThatThrownBy(() -> service.deductStock(List.of(detail)))
                .isInstanceOf(BizIllegalException.class);
    }

    @Test
    void acceptsOnlyExactlyOneUpdatedRow() {
        ItemMapper mapper = mock(ItemMapper.class);
        ItemServiceImpl service = service(mapper);
        OrderDetailDTO detail = detail(1L, 2);
        when(mapper.updateStock(detail)).thenReturn(1);

        assertThatCode(() -> service.deductStock(List.of(detail))).doesNotThrowAnyException();
        verify(mapper).updateStock(detail);
    }

    private ItemServiceImpl service(ItemMapper mapper) {
        ItemServiceImpl service = new ItemServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private OrderDetailDTO detail(Long itemId, Integer num) {
        return new OrderDetailDTO().setItemId(itemId).setNum(num);
    }
}
