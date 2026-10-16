package com.dongqh.luckyhub.service.scope.impl;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.service.scope.DataScopeService;
import com.dongqh.luckyhub.service.scope.UserDataScope;
import org.springframework.stereotype.Service;

@Service
public class DataScopeServiceImpl implements DataScopeService {

    @Override
    public UserDataScope resolveUserScope(Long requestedUserId) {
        long currentUserId = LoginContext.require().userId();
        if (requestedUserId != null
                && requestedUserId.longValue() != currentUserId) {
            throw new com.dongqh.luckyhub.common.exception.ForbiddenException("无权查询其他用户的数据");
        }
        return UserDataScope.one(currentUserId);
    }
}

