package com.dongqh.luckyhub.service.auth;

import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;

/** Maintains the minimal LuckyHub user projection required by marketing asset tables. */
@FunctionalInterface
public interface HmallUserProjectionService {

    void ensureActiveHmallUser(LoginPrincipal principal);
}
