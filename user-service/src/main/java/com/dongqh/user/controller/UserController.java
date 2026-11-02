package com.dongqh.user.controller;

import com.dongqh.user.domain.dto.LoginFormDTO;
import com.dongqh.user.domain.dto.UserAdminFormDTO;
import com.dongqh.user.domain.query.UserPageQuery;
import com.dongqh.user.domain.vo.UserAdminVO;
import com.dongqh.user.domain.vo.UserLoginVO;
import com.dongqh.user.service.IUserService;
import com.hmall.common.domain.PageDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Api(tags = "用户相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @ApiOperation("用户登录接口")
    @PostMapping("login")
    public UserLoginVO login(@RequestBody @Validated LoginFormDTO loginFormDTO){
        return userService.login(loginFormDTO);
    }

    @ApiOperation("扣减余额")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pw", value = "支付密码"),
            @ApiImplicitParam(name = "amount", value = "支付金额")
    })
    @PutMapping("/money/deduct")
    public void deductMoney(@RequestParam("pw") String pw,@RequestParam("amount") Integer amount){
        userService.deductMoney(pw, amount);
    }

    @ApiOperation("分页查询用户")
    @GetMapping("/page")
    public PageDTO<UserAdminVO> queryUsers(@Valid UserPageQuery query) {
        return userService.queryUsers(query);
    }

    @ApiOperation("新增用户")
    @PostMapping
    public void createUser(@Valid @RequestBody UserAdminFormDTO form) {
        userService.createUser(form);
    }

    @ApiOperation("更新用户")
    @PutMapping
    public void updateUser(@Valid @RequestBody UserAdminFormDTO form) {
        userService.updateUser(form);
    }

    @ApiOperation("删除用户")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    @ApiOperation("更新用户状态")
    @PutMapping("/status/{id}/{status}")
    public void updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.updateStatus(id, status);
    }
}

