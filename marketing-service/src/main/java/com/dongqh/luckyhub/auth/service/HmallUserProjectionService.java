package com.dongqh.luckyhub.auth.service;

import com.dongqh.luckyhub.auth.model.LoginPrincipal;

/** Maintains the minimal LuckyHub user projection required by marketing asset tables. */
@FunctionalInterface
public interface HmallUserProjectionService {

    void ensureActiveHmallUser(LoginPrincipal principal);
}
