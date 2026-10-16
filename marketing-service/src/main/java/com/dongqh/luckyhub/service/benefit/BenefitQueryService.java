package com.dongqh.luckyhub.service.benefit;
import com.dongqh.luckyhub.domain.dto.benefit.BenefitQuery;
import com.dongqh.luckyhub.domain.vo.benefit.BenefitView;
import com.dongqh.luckyhub.common.result.PageResponse;
public interface BenefitQueryService { PageResponse<BenefitView> page(BenefitQuery query); BenefitView getById(long id); }
