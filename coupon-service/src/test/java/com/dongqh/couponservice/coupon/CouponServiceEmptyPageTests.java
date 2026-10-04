package com.dongqh.couponservice.coupon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.coupon.entity.UserCoupon;
import com.dongqh.luckyhub.coupon.mapper.CouponIssueRecordMapper;
import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.coupon.service.impl.CouponServiceImpl;
import com.dongqh.luckyhub.coupon.service.port.CouponCatalogPort;
import com.dongqh.luckyhub.coupon.service.port.CouponUserEligibilityPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponServiceEmptyPageTests {

    @Test
    void pageMineReturnsEmptyPageWithoutQueryingTemplatesWhenUserHasNoCoupons() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        CouponServiceImpl service = new CouponServiceImpl(
                templates,
                coupons,
                mock(CouponIssueRecordMapper.class),
                mock(CouponUserEligibilityPort.class),
                mock(CouponCatalogPort.class));
        Page<UserCoupon> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(coupons.selectPage(ArgumentMatchers.<Page<UserCoupon>>any(),
                ArgumentMatchers.<Wrapper<UserCoupon>>any())).thenReturn(emptyPage);

        var response = service.pageMine(42L, 1, 10);

        assertThat(response.records()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.page()).isEqualTo(1);
        verify(templates, never()).selectBatchIds(anyCollection());
    }
}
