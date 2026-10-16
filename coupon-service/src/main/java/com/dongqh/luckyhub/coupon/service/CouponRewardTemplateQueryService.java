package com.dongqh.luckyhub.service.coupon;

import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.luckyhub.domain.po.coupon.CouponTemplate;
import com.dongqh.luckyhub.mapper.coupon.CouponTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CouponRewardTemplateQueryService {
    private final CouponTemplateMapper templates;

    public CouponRewardTemplateQueryService(CouponTemplateMapper templates) {
        this.templates = templates;
    }

    public CouponRewardTemplateDTO getEnabled(long templateId) {
        CouponTemplate template = templates.selectById(templateId);
        if (template == null || !Integer.valueOf(1).equals(template.getStatus())) {
            throw new ResponseStatusException(NOT_FOUND, "优惠券模板不可用");
        }
        return new CouponRewardTemplateDTO(template.getId(), template.getTemplateCode(), template.getStatus());
    }
}
