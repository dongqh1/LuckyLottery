package com.dongqh.luckyhub.service.auth;

import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;
import com.dongqh.luckyhub.mapper.coupon.UserCouponMapper;
import com.dongqh.luckyhub.domain.po.rbac.SysUser;
import com.dongqh.luckyhub.mapper.rbac.SysUserMapper;
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

        service.ensureActiveHmallUser(new LoginPrincipal(2L, "2", null));

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

        service.ensureActiveHmallUser(new LoginPrincipal(2L, "2", null));

        verify(mapper, never()).insertHmallProjection(2L, "hmall:2", "商城用户 2");
        verify(mapper).insertDefaultUserRole(2L);
        verify(coupons).issueHmallCheckoutCoupons(2L);
        verify(coupons).recordHmallCheckoutCouponIssues(2L);
    }
}

