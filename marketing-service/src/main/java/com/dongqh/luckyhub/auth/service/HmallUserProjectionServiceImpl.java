package com.dongqh.luckyhub.auth.service;

import com.dongqh.luckyhub.auth.enums.AuthErrorCode;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HmallUserProjectionServiceImpl implements HmallUserProjectionService {

    private final SysUserMapper userMapper;
    private final UserCouponMapper userCouponMapper;

    public HmallUserProjectionServiceImpl(
            SysUserMapper userMapper,
            UserCouponMapper userCouponMapper
    ) {
        this.userMapper = userMapper;
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    @Transactional
    public void ensureActiveHmallUser(LoginPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId() <= 0) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        long userId = principal.userId();
        if (userMapper.selectById(userId) == null) {
            userMapper.insertHmallProjection(
                    userId,
                    "hmall:" + userId,
                    "商城用户 " + userId
            );
        }
        userMapper.insertDefaultUserRole(userId);
        userCouponMapper.issueHmallCheckoutCoupons(userId);
        userCouponMapper.recordHmallCheckoutCouponIssues(userId);
    }
}
