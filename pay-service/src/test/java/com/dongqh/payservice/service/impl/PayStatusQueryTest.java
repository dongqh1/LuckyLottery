package com.dongqh.payservice.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dongqh.payservice.domain.po.PayOrder;
import com.dongqh.payservice.enums.PayStatus;
import com.dongqh.payservice.mapper.PayOrderMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class PayStatusQueryTest {

    @Mock
    private PayOrderMapper payOrderMapper;

    private PayOrderServiceImpl service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayOrder.class);
    }

    @BeforeEach
    void setUp() {
        service = new PayOrderServiceImpl(null, null, payOrderMapper, null, null);
        UserContext.setUser(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void returnsStatusOnlyFromAPayOrderOwnedByCurrentUser() {
        when(payOrderMapper.selectOne(any(Wrapper.class))).thenReturn(
                new PayOrder().setId(88L).setBizUserId(7L)
                        .setStatus(PayStatus.TRADE_SUCCESS.getValue()));

        Integer status = service.queryPayStatus(88L);

        assertThat(status).isEqualTo(PayStatus.TRADE_SUCCESS.getValue());
        ArgumentCaptor<Wrapper<PayOrder>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(payOrderMapper).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("id", "biz_user_id");
    }

    @Test
    void rejectsMissingOrUnownedPayOrder() {
        when(payOrderMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.queryPayStatus(88L))
                .isInstanceOf(BadRequestException.class);
    }
}
