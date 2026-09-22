package com.dongqh.luckyhub.auth.service;

import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HmallUserProjectionServiceTests {

    @Test
    void insertsMissingHmallUserWithSameId() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        when(mapper.selectById(2L)).thenReturn(null);
        HmallUserProjectionService service = new HmallUserProjectionServiceImpl(mapper, coupons);

        service.ensureActiveHmallUser(new LoginPrincipal(2L, "2", null, "USER"));

        verify(mapper).insertHmallProjection(2L, "hmall:2", "商城用户 2");
        verify(mapper).insertDefaultUserRole(2L);
        verify(coupons).issueHmallCheckoutCoupons(2L);
        verify(coupons).recordHmallCheckoutCouponIssues(2L);
    }

    @Test
    void repairsRoleAndCouponsForAnExistingProjection() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        SysUser existing = new SysUser();
        existing.setId(2L);
        existing.setStatus(1);
        when(mapper.selectById(2L)).thenReturn(existing);
        HmallUserProjectionService service = new HmallUserProjectionServiceImpl(mapper, coupons);

        service.ensureActiveHmallUser(new LoginPrincipal(2L, "2", null, "USER"));

        verify(mapper, never()).insertHmallProjection(2L, "hmall:2", "商城用户 2");
        verify(mapper).insertDefaultUserRole(2L);
        verify(coupons).issueHmallCheckoutCoupons(2L);
        verify(coupons).recordHmallCheckoutCouponIssues(2L);
    }
}
