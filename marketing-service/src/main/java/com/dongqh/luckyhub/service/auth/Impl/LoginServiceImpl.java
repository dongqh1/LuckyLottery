package com.dongqh.luckyhub.service.auth.Impl;

import com.dongqh.luckyhub.domain.dto.auth.LoginCommand;
import com.dongqh.luckyhub.domain.model.auth.AuthenticatedUser;
import com.dongqh.luckyhub.security.auth.JwtService;
import com.dongqh.luckyhub.security.auth.SessionService;
import com.dongqh.luckyhub.service.auth.AuthenticationService;
import com.dongqh.luckyhub.service.auth.LoginService;
import com.dongqh.luckyhub.domain.vo.auth.LoginView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private SessionService sessionService;

    @Override
    public LoginView login(LoginCommand command) {
        // 验证是否可以登录
        AuthenticatedUser  user =
                authenticationService.authenticate(command);
        //生成sessionId
        String sessionId = jwtService.newSessionId();
        //将SessionId保存到redis
        sessionService.create(sessionId, user.userId(), jwtService.getExpireSeconds());
        //生成JWT
        String token = jwtService.generate(user, sessionId);
        //封装成LoginView




        return new LoginView(
                user.userId(),
                user.username(),
                user.nickname(),
                token,
                "Bearer",
                jwtService.getExpireSeconds()
        );
    }

    @Override
    public void logout(String sessionId) {
        sessionService.remove(sessionId);
    }


}
