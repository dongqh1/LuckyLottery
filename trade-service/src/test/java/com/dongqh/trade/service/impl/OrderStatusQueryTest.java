package com.dongqh.trade.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dongqh.trade.domain.po.Order;
import com.dongqh.trade.mapper.OrderMapper;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class OrderStatusQueryTest {

    @Mock
    private OrderMapper orderMapper;

    private OrderServiceImpl service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "baseMapper", orderMapper);
        UserContext.setUser(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void returnsStatusOnlyFromAnOrderOwnedByCurrentUser() {
        when(orderMapper.selectOne(any(Wrapper.class)))
                .thenReturn(new Order().setId(99L).setUserId(7L).setStatus(2));

        Integer status = service.queryOrderStatus(99L);

        assertThat(status).isEqualTo(2);
        ArgumentCaptor<Wrapper<Order>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("id", "user_id");
    }

    @Test
    void rejectsMissingOrUnownedOrder() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.queryOrderStatus(99L))
                .isInstanceOf(BadRequestException.class);
    }
}
