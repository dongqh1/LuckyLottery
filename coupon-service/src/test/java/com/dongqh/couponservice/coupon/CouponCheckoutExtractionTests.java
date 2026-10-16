package com.dongqh.couponservice.coupon;

import com.dongqh.luckyhub.domain.dto.coupon.CouponCheckoutCommand;
import com.dongqh.luckyhub.domain.dto.coupon.CouponCheckoutItemCommand;
import com.dongqh.luckyhub.domain.po.coupon.CouponTemplate;
import com.dongqh.luckyhub.domain.po.coupon.UserCoupon;
import com.dongqh.luckyhub.enums.coupon.CouponScopeType;
import com.dongqh.luckyhub.enums.coupon.CouponType;
import com.dongqh.luckyhub.enums.coupon.UserCouponStatus;
import com.dongqh.luckyhub.coupon.integration.CouponIntegrationService;
import com.dongqh.luckyhub.mapper.coupon.CouponTemplateMapper;
import com.dongqh.luckyhub.mapper.coupon.UserCouponMapper;
import com.dongqh.luckyhub.service.coupon.impl.CouponCheckoutServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponCheckoutExtractionTests {

    @Test
    void checkoutUsesOneIntegrationServiceAndRetainsMembershipRounding() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        CouponIntegrationService integration = mock(CouponIntegrationService.class);
        CouponCheckoutServiceImpl service = new CouponCheckoutServiceImpl(
                templates, coupons, integration);

        CouponTemplate template = new CouponTemplate();
        template.setId(20L);
        template.setTemplateName("Checkout coupon");
        template.setCouponType(CouponType.NO_THRESHOLD);
        template.setThresholdCent(0L);
        template.setDiscountCent(200L);
        template.setScopeType(CouponScopeType.ALL);
        template.setValidFrom(LocalDateTime.now().minusDays(1));
        template.setValidUntil(LocalDateTime.now().plusDays(1));
        template.setStackableWithMembership(true);
        template.setStatus(1);

        UserCoupon coupon = new UserCoupon();
        coupon.setId(7L);
        coupon.setTemplateId(20L);
        coupon.setUserId(1L);
        coupon.setStatus(UserCouponStatus.AVAILABLE);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidUntil(LocalDateTime.now().plusDays(1));

        when(coupons.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(coupon));
        when(templates.selectBatchIds(List.of(20L))).thenReturn(List.of(template));
        when(integration.getItem(55L)).thenReturn(new CouponIntegrationService.ItemSnapshot(
                55L, "Item", 105, 3, "Category", "Brand", 1));
        when(integration.activeMembershipBenefit(1L)).thenReturn(Optional.of(
                new CouponIntegrationService.MembershipBenefit(9_000)));

        var applicable = service.listApplicable(1L,
                new CouponCheckoutCommand(List.of(new CouponCheckoutItemCommand(55L, 1))));

        assertThat(applicable).singleElement().satisfies(view ->
                assertThat(view.discountCent()).isEqualTo(95L));
    }

    @Test
    void membershipDiscountStillRequiresCouponStacking() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        CouponIntegrationService integration = mock(CouponIntegrationService.class);
        CouponCheckoutServiceImpl service = new CouponCheckoutServiceImpl(
                templates, coupons, integration);

        CouponTemplate template = new CouponTemplate();
        template.setId(20L);
        template.setTemplateName("Non-stackable");
        template.setThresholdCent(0L);
        template.setDiscountCent(200L);
        template.setScopeType(CouponScopeType.ALL);
        template.setValidFrom(LocalDateTime.now().minusDays(1));
        template.setValidUntil(LocalDateTime.now().plusDays(1));
        template.setStackableWithMembership(false);
        template.setStatus(1);
        UserCoupon coupon = new UserCoupon();
        coupon.setId(7L);
        coupon.setTemplateId(20L);
        coupon.setUserId(1L);
        coupon.setStatus(UserCouponStatus.AVAILABLE);
        coupon.setValidUntil(LocalDateTime.now().plusDays(1));

        when(coupons.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(coupon));
        when(templates.selectBatchIds(List.of(20L))).thenReturn(List.of(template));
        when(integration.getItem(55L)).thenReturn(new CouponIntegrationService.ItemSnapshot(
                55L, "Item", 1_000, 3, "Category", "Brand", 1));
        when(integration.activeMembershipBenefit(1L)).thenReturn(Optional.of(
                new CouponIntegrationService.MembershipBenefit(9_000)));

        var applicable = service.listApplicable(1L,
                new CouponCheckoutCommand(List.of(new CouponCheckoutItemCommand(55L, 1))));

        assertThat(applicable).isEmpty();
    }
}
