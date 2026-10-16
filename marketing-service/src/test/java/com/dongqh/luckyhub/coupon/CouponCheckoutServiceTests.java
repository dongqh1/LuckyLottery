package com.dongqh.luckyhub.coupon;

import com.dongqh.luckyhub.domain.dto.coupon.CouponCheckoutCommand;
import com.dongqh.luckyhub.domain.dto.coupon.CouponCheckoutItemCommand;
import com.dongqh.luckyhub.domain.po.coupon.CouponTemplate;
import com.dongqh.luckyhub.domain.po.coupon.UserCoupon;
import com.dongqh.luckyhub.enums.coupon.CouponScopeType;
import com.dongqh.luckyhub.enums.coupon.CouponType;
import com.dongqh.luckyhub.enums.coupon.UserCouponStatus;
import com.dongqh.luckyhub.mapper.coupon.CouponTemplateMapper;
import com.dongqh.luckyhub.mapper.coupon.UserCouponMapper;
import com.dongqh.luckyhub.service.coupon.impl.CouponCheckoutServiceImpl;
import com.dongqh.luckyhub.domain.hmall.integration.HmallCatalogClient;
import com.dongqh.luckyhub.domain.hmall.integration.HmallItemView;
import com.dongqh.luckyhub.service.membership.MembershipService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponCheckoutServiceTests {

    @Test
    void rimowaBrandCouponIsListedOnlyForRimowaBasket() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        HmallCatalogClient catalog = mock(HmallCatalogClient.class);
        MembershipService memberships = mock(MembershipService.class);
        CouponCheckoutServiceImpl service = new CouponCheckoutServiceImpl(
                templates, coupons, catalog, memberships);

        CouponTemplate template = template(CouponScopeType.BRAND);
        template.setHmallBrand("RIMOWA");
        UserCoupon coupon = coupon();
        when(coupons.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(coupon));
        when(templates.selectBatchIds(List.of(20L))).thenReturn(List.of(template));
        when(memberships.activeBenefit(1L)).thenReturn(Optional.empty());
        when(catalog.getItem(561178L)).thenReturn(new HmallItemView(
                561178L, "RIMOWA 30 英寸拉杆箱", 1300, 10, null,
                "拉杆箱", "RIMOWA", "30英寸", 1));
        when(catalog.getItem(546872L)).thenReturn(new HmallItemView(
                546872L, "商务拉杆箱", 27500, 10, null,
                "拉杆箱", "BOYI", "23英寸", 1));

        var matching = service.listApplicable(1L,
                new CouponCheckoutCommand(List.of(new CouponCheckoutItemCommand(561178L, 1))));
        var otherBrand = service.listApplicable(1L,
                new CouponCheckoutCommand(List.of(new CouponCheckoutItemCommand(546872L, 1))));

        assertThat(matching).singleElement().satisfies(view -> {
            assertThat(view.userCouponId()).isEqualTo("2096098706886033409");
            assertThat(view.scopeType()).isEqualTo(CouponScopeType.BRAND);
            assertThat(view.scopeValue()).isEqualTo("RIMOWA");
            assertThat(view.discountCent()).isEqualTo(200L);
        });
        assertThat(otherBrand).isEmpty();
    }

    private CouponTemplate template(CouponScopeType scopeType) {
        CouponTemplate template = new CouponTemplate();
        template.setId(20L);
        template.setTemplateName("RIMOWA 品牌礼遇券");
        template.setCouponType(CouponType.NO_THRESHOLD);
        template.setThresholdCent(0L);
        template.setDiscountCent(200L);
        template.setScopeType(scopeType);
        template.setValidFrom(LocalDateTime.now().minusDays(1));
        template.setValidUntil(LocalDateTime.now().plusDays(10));
        template.setStackableWithMembership(true);
        template.setStatus(1);
        return template;
    }

    private UserCoupon coupon() {
        UserCoupon coupon = new UserCoupon();
        coupon.setId(2096098706886033409L);
        coupon.setTemplateId(20L);
        coupon.setUserId(1L);
        coupon.setStatus(UserCouponStatus.AVAILABLE);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidUntil(LocalDateTime.now().plusDays(10));
        return coupon;
    }
}
