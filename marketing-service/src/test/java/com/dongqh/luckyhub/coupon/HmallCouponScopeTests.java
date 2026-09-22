package com.dongqh.luckyhub.coupon;

import com.dongqh.luckyhub.coupon.dto.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.coupon.enums.CouponScopeType;
import com.dongqh.luckyhub.coupon.enums.CouponType;
import com.dongqh.luckyhub.coupon.mapper.CouponIssueRecordMapper;
import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.coupon.entity.CouponTemplate;
import com.dongqh.luckyhub.coupon.entity.UserCoupon;
import com.dongqh.luckyhub.coupon.enums.UserCouponStatus;
import com.dongqh.luckyhub.coupon.service.impl.CouponServiceImpl;
import com.dongqh.luckyhub.integration.hmall.HmallCatalogClient;
import com.dongqh.luckyhub.integration.hmall.HmallItemView;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HmallCouponScopeTests {
    @Test
    void createsItemScopedCouponOnlyAfterValidatingHmallItem() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        HmallCatalogClient catalog = mock(HmallCatalogClient.class);
        when(catalog.getItem(317578L)).thenReturn(new HmallItemView(317578L, "商城行李箱", 28900, 8, "/item.png", "箱包", "品牌", "规格", 1));

        var service = new CouponServiceImpl(templates, mock(UserCouponMapper.class), mock(CouponIssueRecordMapper.class), mock(SysUserMapper.class), catalog);
        var view = service.createTemplate(new CreateCouponTemplateCommand(
                "HMALL-ITEM-10", "商城行李箱券", CouponType.NO_THRESHOLD, 0L, 1000L, null,
                CouponScopeType.ITEM, 317578L, null,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1), 1, true));

        assertThat(view.scopeType()).isEqualTo(CouponScopeType.ITEM);
        assertThat(view.hmallItemId()).isEqualTo(317578L);
        verify(catalog).getItem(317578L);
        verify(templates).insert(any(CouponTemplate.class));
    }

    @Test
    void rejectsLockWhenHmallItemDoesNotMatchItemScopedCoupon() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        UserCoupon coupon = new UserCoupon();
        coupon.setId(7L); coupon.setUserId(1L); coupon.setTemplateId(2L); coupon.setStatus(UserCouponStatus.AVAILABLE);
        CouponTemplate template = new CouponTemplate();
        template.setId(2L); template.setStatus(1); template.setCouponType(CouponType.NO_THRESHOLD);
        template.setThresholdCent(0L); template.setDiscountCent(1000L); template.setScopeType(CouponScopeType.ITEM);
        template.setHmallItemId(317578L); template.setValidFrom(LocalDateTime.now().minusMinutes(1)); template.setValidUntil(LocalDateTime.now().plusDays(1)); template.setStackableWithMembership(true);
        when(coupons.selectById(7L)).thenReturn(coupon);
        when(templates.selectById(2L)).thenReturn(template);

        var service = new CouponServiceImpl(templates, coupons, mock(CouponIssueRecordMapper.class), mock(SysUserMapper.class), mock(HmallCatalogClient.class));
        assertThatThrownBy(() -> service.lockForOrder(1, 7, "HMALL-ORDER-1", 999L, 28900L, false))
                .hasMessageContaining("不适用");
        verify(coupons, never()).lock(anyLong(), anyLong(), anyString());
    }
}
