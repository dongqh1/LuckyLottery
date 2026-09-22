package com.hmall.controller;

import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.AddressDTO;
import com.hmall.domain.po.Address;
import com.hmall.service.IAddressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressControllerTest {
    private IAddressService service;
    private AddressController controller;

    @BeforeEach
    void setUp() {
        service = mock(IAddressService.class);
        controller = new AddressController(service);
        UserContext.setUser(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void createsAddressForCurrentUserAndIgnoresIncomingId() {
        AddressDTO request = address();
        request.setId(999L);
        when(service.save(argThat(value -> value.getUserId().equals(7L) && value.getId() == null)))
                .thenReturn(true);

        Long id = controller.addAddress(request);

        verify(service).save(argThat(value -> value.getUserId().equals(7L) && value.getId() == null));
        assertThat(id).isNull();
    }

    @Test
    void rejectsUpdatingAnotherUsersAddress() {
        Address foreign = new Address().setId(3L).setUserId(8L);
        when(service.getById(3L)).thenReturn(foreign);

        assertThatThrownBy(() -> controller.updateAddress(3L, address()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不属于当前登录用户");
    }

    @Test
    void rejectsDeletingMissingAddress() {
        when(service.getById(3L)).thenReturn(null);

        assertThatThrownBy(() -> controller.deleteAddress(3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不存在");
    }

    private AddressDTO address() {
        AddressDTO dto = new AddressDTO();
        dto.setProvince("北京市");
        dto.setCity("北京市");
        dto.setTown("海淀区");
        dto.setStreet("中关村大街 1 号");
        dto.setContact("张三");
        dto.setMobile("13800138000");
        dto.setIsDefault(0);
        return dto;
    }
}
