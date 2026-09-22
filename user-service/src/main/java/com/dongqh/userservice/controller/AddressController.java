package com.dongqh.userservice.controller;


import com.dongqh.userservice.domain.dto.AddressDTO;
import com.dongqh.userservice.domain.po.Address;
import com.dongqh.userservice.service.IAddressService;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
@Api(tags = "收货地址管理接口")
public class AddressController {

    private final IAddressService addressService;

    @ApiOperation("根据id查询地址")
    @GetMapping("{addressId}")
    public AddressDTO findAddressById(@ApiParam("地址id") @PathVariable("addressId") Long id) {
        // 1.根据id查询
        Address address = addressService.getById(id);
        if (address == null) {
            throw new BadRequestException("地址不存在");
        }
        // 2.判断当前用户
        Long userId = UserContext.getUser();
        if(!address.getUserId().equals(userId)){
            throw new BadRequestException("地址不属于当前登录用户");
        }
        return BeanUtils.copyBean(address, AddressDTO.class);
    }



    @ApiOperation("查询当前用户地址列表")
    @GetMapping
    public List<AddressDTO> findMyAddresses() {
        // 1.查询列表
        List<Address> list = addressService.query().eq("user_id", UserContext.getUser()).list();
        // 2.判空
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 3.转vo
        return BeanUtils.copyList(list, AddressDTO.class);
    }

    @ApiOperation("新增收货地址")
    @PostMapping
    public Long addAddress(@RequestBody AddressDTO addressDTO) {
        Address address = BeanUtils.copyBean(addressDTO, Address.class);
        address.setId(null);
        address.setUserId(UserContext.getUser());
        addressService.save(address);
        return address.getId();
    }

    @ApiOperation("修改收货地址")
    @PutMapping("{addressId}")
    public void updateAddress(@PathVariable("addressId") Long id, @RequestBody AddressDTO addressDTO) {
        requireOwnedAddress(id);
        Address address = BeanUtils.copyBean(addressDTO, Address.class);
        address.setId(id);
        address.setUserId(UserContext.getUser());
        addressService.updateById(address);
    }

    @ApiOperation("删除收货地址")
    @DeleteMapping("{addressId}")
    public void deleteAddress(@PathVariable("addressId") Long id) {
        requireOwnedAddress(id);
        addressService.removeById(id);
    }

    private Address requireOwnedAddress(Long id) {
        Address address = addressService.getById(id);
        if (address == null) {
            throw new BadRequestException("地址不存在");
        }
        if (!address.getUserId().equals(UserContext.getUser())) {
            throw new BadRequestException("地址不属于当前登录用户");
        }
        return address;
    }
}
