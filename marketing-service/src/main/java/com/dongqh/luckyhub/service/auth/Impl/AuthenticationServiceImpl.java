package com.dongqh.luckyhub.service.auth.Impl;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.domain.dto.auth.LoginCommand;
import com.dongqh.luckyhub.enums.auth.AuthErrorCode;
import com.dongqh.luckyhub.domain.model.auth.AuthenticatedUser;
import com.dongqh.luckyhub.security.auth.PasswordService;
import com.dongqh.luckyhub.service.auth.AuthenticationService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.domain.po.rbac.SysUser;
import com.dongqh.luckyhub.mapper.rbac.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    @Autowired
    private  SysUserMapper userMapper;
    @Autowired
    private  PasswordService passwordService;



    @Override
    public AuthenticatedUser authenticate(LoginCommand command) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, command.username())
                        .last("LIMIT 1")
        );
        // 用户不存在
        if(user == null ||
            !passwordService.matches(command.password(), user.getPassword())
        ) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if(!Integer.valueOf(1).equals(user.getStatus())){
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );

    }
}
