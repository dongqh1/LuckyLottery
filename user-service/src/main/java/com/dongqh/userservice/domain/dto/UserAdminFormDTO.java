package com.dongqh.userservice.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class UserAdminFormDTO {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private Integer status;
}

