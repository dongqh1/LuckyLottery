package com.dongqh.couponservice.coupon;

import com.dongqh.luckyhub.domain.dto.coupon.IssueCouponCommand;
import com.dongqh.luckyhub.domain.po.coupon.CouponTemplate;
import com.dongqh.luckyhub.domain.po.coupon.CouponIssueRecord;
import com.dongqh.luckyhub.domain.po.coupon.UserCoupon;
import com.dongqh.luckyhub.enums.coupon.CouponErrorCode;
import com.dongqh.luckyhub.coupon.domain.exception.CouponDomainException;
import com.dongqh.luckyhub.coupon.integration.CouponIntegrationService;
import com.dongqh.luckyhub.mapper.coupon.CouponIssueRecordMapper;
import com.dongqh.luckyhub.mapper.coupon.CouponTemplateMapper;
import com.dongqh.luckyhub.mapper.coupon.UserCouponMapper;
import com.dongqh.luckyhub.service.coupon.impl.CouponServiceImpl;
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
        CouponIntegrationService integration = mock(CouponIntegrationService.class);
        CouponTemplate template = new CouponTemplate();
        template.setId(2L);
        template.setStatus(1);
        template.setPerUserLimit(1);
        template.setValidFrom(LocalDateTime.now().minusDays(1));
        template.setValidUntil(LocalDateTime.now().plusDays(1));
        when(templates.lockById(2L)).thenReturn(template);
        when(integration.isEnabledUser(9L)).thenReturn(false);

        CouponServiceImpl service = new CouponServiceImpl(templates, coupons, issues, integration);

        assertThatThrownBy(() -> service.issue(new IssueCouponCommand("issue-1", "coupon-1", 2L, 9L)))
                .isInstanceOf(CouponDomainException.class)
                .extracting(exception -> ((CouponDomainException) exception).errorCode())
                .isEqualTo(CouponErrorCode.COUPON_UNAVAILABLE);
        verify(coupons, never()).insert(org.mockito.ArgumentMatchers.any(UserCoupon.class));
        verify(issues, never()).insert(org.mockito.ArgumentMatchers.any(CouponIssueRecord.class));
    }
}
