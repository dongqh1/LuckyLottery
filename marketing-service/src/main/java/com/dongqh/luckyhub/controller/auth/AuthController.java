package com.dongqh.luckyhub.controller.auth;


import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.domain.dto.auth.LoginCommand;
import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;
import com.dongqh.luckyhub.service.auth.LoginService;
import com.dongqh.luckyhub.domain.vo.auth.CurrentUserView;
import com.dongqh.luckyhub.domain.vo.auth.LoginView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.po.rbac.SysUser;
import com.dongqh.luckyhub.mapper.rbac.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "登录关联", description = "登录拦截认证接口")
public class AuthController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private SysUserMapper users;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public ApiResponse<LoginView> login( @Valid @RequestBody
                                             LoginCommand command) {

        return ApiResponse.success(loginService.login(command));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public ApiResponse logout() {
        LoginPrincipal user = LoginContext.require();
        loginService.logout(user.sessionId());
        return ApiResponse.success();
    }

    @GetMapping("/me")
    @Operation(summary = "主页")
    public ApiResponse me(){
        LoginPrincipal principal = LoginContext.require();
        Long id = principal.userId();

        SysUser user = users.selectById(id);
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        }
        CurrentUserView view = new CurrentUserView(
                user.getId(), user.getUsername(), user.getNickname(), user.getStatus());

        return ApiResponse.success(view);
    }



}
