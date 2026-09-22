package com.hmall.service.impl;

import cn.hutool.jwt.JWT;
import com.hmall.config.JwtProperties;
import com.hmall.domain.dto.LoginFormDTO;
import com.hmall.domain.po.User;
import com.hmall.domain.vo.UserLoginVO;
import com.hmall.enums.UserStatus;
import com.hmall.mapper.UserMapper;
import com.hmall.utils.JwtTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceLoginTokenTest {

    @Test
    void loginTokenContainsPersistedRoleClaim() throws Exception {
        User user = new User()
                .setId(1L)
                .setUsername("admin")
                .setPassword("encoded")
                .setStatus(UserStatus.NORMAL)
                .setBalance(1000)
                .setRole("ADMIN");

        UserLoginVO login = login(user);

        assertThat(JWT.of(login.getToken()).getPayload("user")).hasToString("1");
        assertThat(JWT.of(login.getToken()).getPayload("role")).isEqualTo("ADMIN");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void loginTokenFallsBackToUserWhenPersistedRoleIsMissing(String role) throws Exception {
        User user = new User()
                .setId(2L)
                .setUsername("customer")
                .setPassword("encoded")
                .setStatus(UserStatus.NORMAL)
                .setBalance(1000)
                .setRole(role);

        UserLoginVO login = login(user);

        assertThat(JWT.of(login.getToken()).getPayload("role")).isEqualTo("USER");
    }

    private UserLoginVO login(User user) throws Exception {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectOne(any())).thenReturn(user);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        JwtProperties properties = new JwtProperties();
        properties.setTokenTTL(Duration.ofMinutes(5));
        UserServiceImpl service = new UserServiceImpl(passwordEncoder, new JwtTool(keyPair), properties);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        LoginFormDTO form = new LoginFormDTO();
        form.setUsername(user.getUsername());
        form.setPassword("secret");
        return service.login(form);
    }
}
