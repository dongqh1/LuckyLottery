package com.dongqh.luckyhub.controller.shipping;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.shipping.CreateShippingAddressCommand;
import com.dongqh.luckyhub.domain.dto.shipping.UpdateShippingAddressCommand;
import com.dongqh.luckyhub.service.shipping.ShippingAddressService;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingAddressView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/shipping/addresses")
public class ShippingAddressController {

    private final ShippingAddressService service;

    public ShippingAddressController(ShippingAddressService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShippingAddressView> create(@Valid @RequestBody CreateShippingAddressCommand command) {
        return ApiResponse.success(service.create(LoginContext.require().userId(), command));
    }

    @GetMapping
    public ApiResponse<List<ShippingAddressView>> list() {
        return ApiResponse.success(service.list(LoginContext.require().userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShippingAddressView> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody UpdateShippingAddressCommand command
    ) {
        return ApiResponse.success(service.update(LoginContext.require().userId(), id, command));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(LoginContext.require().userId(), id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/default")
    public ApiResponse<ShippingAddressView> makeDefault(@PathVariable @Positive long id) {
        return ApiResponse.success(service.makeDefault(LoginContext.require().userId(), id));
    }
}

