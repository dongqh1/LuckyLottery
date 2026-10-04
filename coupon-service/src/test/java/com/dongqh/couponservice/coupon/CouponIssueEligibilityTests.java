package com.dongqh.couponservice.coupon;

import com.dongqh.luckyhub.coupon.dto.IssueCouponCommand;
import com.dongqh.luckyhub.coupon.entity.CouponTemplate;
import com.dongqh.luckyhub.coupon.entity.CouponIssueRecord;
import com.dongqh.luckyhub.coupon.entity.UserCoupon;
import com.dongqh.luckyhub.coupon.enums.CouponErrorCode;
import com.dongqh.luckyhub.coupon.exception.CouponDomainException;
import com.dongqh.luckyhub.coupon.mapper.CouponIssueRecordMapper;
import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.coupon.service.impl.CouponServiceImpl;
import com.dongqh.luckyhub.coupon.service.port.CouponCatalogPort;
import com.dongqh.luckyhub.coupon.service.port.CouponUserEligibilityPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponIssueEligibilityTests {

    @Test
    void issueFailsClosedWhenUserServiceSaysUserIsDisabled() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        CouponIssueRecordMapper issues = mock(CouponIssueRecordMapper.class);
        CouponUserEligibilityPort users = mock(CouponUserEligibilityPort.class);
        CouponCatalogPort catalog = mock(CouponCatalogPort.class);
        CouponTemplate template = new CouponTemplate();
        template.setId(2L);
        template.setStatus(1);
        template.setPerUserLimit(1);
        template.setValidFrom(LocalDateTime.now().minusDays(1));
        template.setValidUntil(LocalDateTime.now().plusDays(1));
        when(templates.lockById(2L)).thenReturn(template);
        when(users.isEnabled(9L)).thenReturn(false);

        CouponServiceImpl service = new CouponServiceImpl(templates, coupons, issues, users, catalog);

        assertThatThrownBy(() -> service.issue(new IssueCouponCommand("issue-1", "coupon-1", 2L, 9L)))
                .isInstanceOf(CouponDomainException.class)
                .extracting(exception -> ((CouponDomainException) exception).errorCode())
                .isEqualTo(CouponErrorCode.COUPON_UNAVAILABLE);
        verify(coupons, never()).insert(org.mockito.ArgumentMatchers.any(UserCoupon.class));
        verify(issues, never()).insert(org.mockito.ArgumentMatchers.any(CouponIssueRecord.class));
    }
}
