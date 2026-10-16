package com.dongqh.hmapi.client;


import com.dongqh.hmapi.dto.AddressDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient("user-service")
public interface UserClient {


    @GetMapping("/addresses/{addressId}")
    AddressDTO findAddressById(@PathVariable("addressId") Long id);

    @GetMapping("/internal/users/{userId}/enabled")
    Boolean isEnabled(@PathVariable("userId") Long userId);

    @PutMapping("/users/money/deduct")
    void deductMoney(
            @RequestParam("pw") String password,
            @RequestParam("amount") Integer amount);
}
