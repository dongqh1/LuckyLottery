package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.domain.dto.shipping.CreateShippingAddressCommand;
import com.dongqh.luckyhub.domain.dto.shipping.UpdateShippingAddressCommand;
import com.dongqh.luckyhub.domain.po.shipping.UserShippingAddress;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingAddressView;

import java.util.List;

public interface ShippingAddressService {
    ShippingAddressView create(long userId, CreateShippingAddressCommand command);
    List<ShippingAddressView> list(long userId);
    ShippingAddressView update(long userId, long addressId, UpdateShippingAddressCommand command);
    void delete(long userId, long addressId);
    ShippingAddressView makeDefault(long userId, long addressId);
    UserShippingAddress requireOwnedActive(long userId, long addressId);
}
