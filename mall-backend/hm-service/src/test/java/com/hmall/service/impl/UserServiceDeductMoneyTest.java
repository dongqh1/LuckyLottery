package com.hmall.service.impl;

import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.UserContext;
import com.hmall.config.JwtProperties;
import com.hmall.domain.po.User;
import com.hmall.mapper.UserMapper;
import com.hmall.utils.JwtTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceDeductMoneyTest {

    @AfterEach
    void clearUser() {
        UserContext.removeUser();
    }

    @Test
    void deductionFailsWhenGuardedUpdateAffectsNoRow() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectById(9L)).thenReturn(new User().setId(9L).setPassword("encoded"));
        when(mapper.updateMoney(9L, 500)).thenReturn(0);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("pw", "encoded")).thenReturn(true);
        UserServiceImpl service = new UserServiceImpl(encoder, mock(JwtTool.class), new JwtProperties());
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        UserContext.setUser(9L);

        assertThatThrownBy(() -> service.deductMoney("pw", 500))
                .isInstanceOf(BizIllegalException.class);
    }
}
