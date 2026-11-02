package com.dongqh.user.controller;

import com.dongqh.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserEligibilityController {
    private final UserMapper users;

    @GetMapping("/{userId}/enabled")
    public boolean isEnabled(@PathVariable("userId") Long userId) {
        return Integer.valueOf(1).equals(users.selectStatusValueById(userId));
    }
}
