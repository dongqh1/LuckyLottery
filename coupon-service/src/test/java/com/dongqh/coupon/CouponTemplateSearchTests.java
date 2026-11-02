package com.dongqh.coupon;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.coupon.integration.CouponIntegrationService;
import com.dongqh.coupon.controller.CouponAdminController;
import com.dongqh.coupon.domain.po.coupon.CouponTemplate;
import com.dongqh.coupon.enums.coupon.CouponType;
import com.dongqh.coupon.mapper.coupon.CouponIssueRecordMapper;
import com.dongqh.coupon.mapper.coupon.CouponTemplateMapper;
import com.dongqh.coupon.mapper.coupon.UserCouponMapper;
import com.dongqh.coupon.service.coupon.impl.CouponServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponTemplateSearchTests {

    @Test
    void searchesTemplatesAndReturnsFieldsNeededByPrizePicker() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        CouponTemplate template = new CouponTemplate();
        template.setId(19L);
        template.setTemplateCode("WELCOME-10");
        template.setTemplateName("新客满减券");
        template.setCouponType(CouponType.THRESHOLD);
        template.setThresholdCent(10_000L);
        template.setDiscountCent(2_000L);
        template.setStatus(1);

        Page<CouponTemplate> resultPage = new Page<>(1, 20);
        resultPage.setRecords(List.of(template));
        resultPage.setTotal(1);
        when(templates.selectPage(ArgumentMatchers.<Page<CouponTemplate>>any(),
                ArgumentMatchers.<Wrapper<CouponTemplate>>any())).thenReturn(resultPage);

        CouponServiceImpl service = new CouponServiceImpl(
                templates,
                mock(UserCouponMapper.class),
                mock(CouponIssueRecordMapper.class),
                mock(CouponIntegrationService.class));

        var results = service.searchTemplates("新客", 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(19L);
        assertThat(results.get(0).templateName()).isEqualTo("新客满减券");
        assertThat(results.get(0).thresholdCent()).isEqualTo(10_000L);
        assertThat(results.get(0).discountCent()).isEqualTo(2_000L);
    }

    @Test
    void searchClampsLargePageSizeToFifty() {
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        Page<CouponTemplate> resultPage = new Page<>(1, 50);
        resultPage.setRecords(List.of());
        when(templates.selectPage(ArgumentMatchers.<Page<CouponTemplate>>any(),
                ArgumentMatchers.<Wrapper<CouponTemplate>>any())).thenReturn(resultPage);

        CouponServiceImpl service = new CouponServiceImpl(
                templates,
                mock(UserCouponMapper.class),
                mock(CouponIssueRecordMapper.class),
                mock(CouponIntegrationService.class));

        service.searchTemplates("", 5000);

        ArgumentCaptor<Page<CouponTemplate>> page = ArgumentCaptor.forClass(Page.class);
        verify(templates).selectPage(page.capture(), ArgumentMatchers.<Wrapper<CouponTemplate>>any());
        assertThat(page.getValue().getSize()).isEqualTo(50L);
    }

    @Test
    void adminControllerExposesTemplateSearchEndpoint() throws Exception {
        var method = CouponAdminController.class.getDeclaredMethod("searchTemplates", String.class, int.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/coupon-templates");
    }
}
