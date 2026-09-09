package com.dongqh.userservice.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.userservice.domain.dto.UserAdminFormDTO;
import com.dongqh.userservice.domain.po.User;
import com.dongqh.userservice.domain.query.UserPageQuery;
import com.dongqh.userservice.domain.vo.UserAdminVO;
import com.dongqh.userservice.enums.UserStatus;
import com.dongqh.userservice.mapper.UserMapper;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.ForbiddenException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class UserAdminServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(passwordEncoder, null, null);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        UserContext.setUser(1L);
        when(userMapper.selectById(1L)).thenReturn(user(1L, "ADMIN", UserStatus.NORMAL));
        lenient().when(userMapper.insert(any(User.class))).thenReturn(1);
        lenient().when(userMapper.updateById(any(User.class))).thenReturn(1);
        lenient().when(userMapper.deleteById(any(Long.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void pagedResultExposesOnlyAdminViewFieldsAndLegacyStatusValues() {
        User frozen = user(2L, "USER", UserStatus.FROZEN);
        frozen.setUsername("Jack");
        frozen.setPhone("13800000000");
        frozen.setPassword("secret-hash");
        frozen.setBalance(10000);
        Page<User> page = new Page<>(1, 20, 1);
        page.setRecords(Collections.singletonList(frozen));
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageDTO<UserAdminVO> result = service.queryUsers(new UserPageQuery());

        assertThat(result.getList()).singleElement().satisfies(view -> {
            assertThat(view.getUsername()).isEqualTo("Jack");
            assertThat(view.getStatus()).isEqualTo(2);
        });
        assertThat(UserAdminVO.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("password", "balance");
    }

    @Test
    void createHashesPasswordAndAppliesSafeDefaults() {
        UserAdminFormDTO form = new UserAdminFormDTO();
        form.setUsername("new-user");
        form.setPhone("13900000000");
        form.setPassword("plain-password");
        when(passwordEncoder.encode("plain-password")).thenReturn("password-hash");

        service.createUser(form);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("password-hash");
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(saved.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    void updateWithBlankPasswordDoesNotOverwriteStoredHash() {
        UserAdminFormDTO form = new UserAdminFormDTO();
        form.setId(2L);
        form.setUsername("updated");
        form.setPassword("  ");

        service.updateUser(form);

        ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(updated.capture());
        assertThat(updated.getValue().getPassword()).isNull();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void legacyFrozenStatusTwoMapsToServiceFrozenEnum() {
        service.updateStatus(2L, 2);

        ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(2L);
        assertThat(updated.getValue().getStatus()).isEqualTo(UserStatus.FROZEN);
    }

    @Test
    void normalUserCannotUseAdministrationOperations() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "USER", UserStatus.NORMAL));

        assertThatThrownBy(() -> service.queryUsers(new UserPageQuery()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void frozenAdministratorCannotUseAdministrationOperations() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "ADMIN", UserStatus.FROZEN));

        assertThatThrownBy(() -> service.queryUsers(new UserPageQuery()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void administratorCannotDeleteOwnAccount() {
        assertThatThrownBy(() -> service.deleteUser(1L))
                .isInstanceOf(BizIllegalException.class);
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    private User user(Long id, String role, UserStatus status) {
        return new User().setId(id).setRole(role).setStatus(status);
    }
}
