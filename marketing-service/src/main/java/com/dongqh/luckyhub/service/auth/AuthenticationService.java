package com.dongqh.luckyhub.service.auth;

import com.dongqh.luckyhub.domain.dto.auth.LoginCommand;
import com.dongqh.luckyhub.domain.model.auth.AuthenticatedUser;

public interface AuthenticationService {

    AuthenticatedUser authenticate(LoginCommand command);
}
