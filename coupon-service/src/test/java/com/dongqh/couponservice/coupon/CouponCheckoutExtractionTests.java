package com.dongqh.couponservice.coupon;

import com.dongqh.luckyhub.coupon.dto.CouponCheckoutCommand;
import com.dongqh.luckyhub.coupon.dto.CouponCheckoutItemCommand;
import com.dongqh.luckyhub.coupon.entity.CouponTemplate;
import com.dongqh.luckyhub.coupon.entity.UserCoupon;
import com.dongqh.luckyhub.coupon.enums.CouponScopeType;
import com.dongqh.luckyhub.coupon.enums.CouponType;
import com.dongqh.luckyhub.coupon.enums.UserCouponStatus;
import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.coupon.service.impl.CouponCheckoutServiceImpl;
import com.dongqh.luckyhub.coupon.service.port.CouponCatalogPort;
import com.dongqh.luckyhub.coupon.service.port.CouponItemSnapshot;
import com.dongqh.luckyhub.coupon.service.port.CouponMembershipBenefit;
import com.dongqh.luckyhub.coupon.service.port.CouponMembershipBenefitPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponCheckoutExtractionTests {

    @Test
    void checkoutUsesIndependentPortsAndRetainsMembershipRounding() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        CouponCatalogPort catalog = mock(CouponCatalogPort.class);
        CouponMembershipBenefitPort memberships = mock(CouponMembershipBenefitPort.class);
        CouponCheckoutServiceImpl service = new CouponCheckoutServiceImpl(
                templates, coupons, catalog, memberships);

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
        when(catalog.getItem(55L)).thenReturn(new CouponItemSnapshot(
                55L, "Item", 105, 3, "Category", "Brand", 1));
        when(memberships.activeBenefit(1L)).thenReturn(Optional.of(
                new CouponMembershipBenefit(9_000)));

        var applicable = service.listApplicable(1L,
                new CouponCheckoutCommand(List.of(new CouponCheckoutItemCommand(55L, 1))));

        assertThat(applicable).singleElement().satisfies(view ->
                assertThat(view.discountCent()).isEqualTo(95L));
    }

    @Test
    void membershipDiscountStillRequiresCouponStacking() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        CouponCatalogPort catalog = mock(CouponCatalogPort.class);
        CouponMembershipBenefitPort memberships = mock(CouponMembershipBenefitPort.class);
        CouponCheckoutServiceImpl service = new CouponCheckoutServiceImpl(
                templates, coupons, catalog, memberships);

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
        when(catalog.getItem(55L)).thenReturn(new CouponItemSnapshot(
                55L, "Item", 1_000, 3, "Category", "Brand", 1));
        when(memberships.activeBenefit(1L)).thenReturn(Optional.of(
                new CouponMembershipBenefit(9_000)));

        var applicable = service.listApplicable(1L,
                new CouponCheckoutCommand(List.of(new CouponCheckoutItemCommand(55L, 1))));

        assertThat(applicable).isEmpty();
    }
}
