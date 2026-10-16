package com.dongqh.luckyhub.service.auth;

import com.dongqh.luckyhub.domain.dto.auth.LoginCommand;
import com.dongqh.luckyhub.domain.vo.auth.LoginView;
import jakarta.validation.Valid;

public interface LoginService {
    LoginView login(@Valid LoginCommand command);

    void logout(String sessionId);
}
