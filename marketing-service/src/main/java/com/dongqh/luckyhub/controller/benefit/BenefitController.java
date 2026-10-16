package com.dongqh.luckyhub.controller.benefit;
import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.domain.dto.benefit.BenefitQuery; import com.dongqh.luckyhub.service.benefit.BenefitQueryService; import com.dongqh.luckyhub.domain.vo.benefit.BenefitView;
import com.dongqh.luckyhub.domain.dto.shipping.ClaimPhysicalBenefitCommand; import com.dongqh.luckyhub.service.shipping.PhysicalClaimService; import com.dongqh.luckyhub.domain.vo.shipping.ShippingOrderView;
import com.dongqh.luckyhub.common.result.*; import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.validation.Valid; import jakarta.validation.constraints.Positive; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/benefits") @Tag(name="用户权益",description="用户权益和管理查询接口")
public class BenefitController { private final BenefitQueryService service; private final PhysicalClaimService claims; public BenefitController(BenefitQueryService service, PhysicalClaimService claims){this.service=service;this.claims=claims;}
 @GetMapping @Operation(summary="分页查询权益") public ApiResponse<PageResponse<BenefitView>> page(@Valid @ModelAttribute BenefitQuery query){return ApiResponse.success(service.page(query));}
 @GetMapping("/{id}") @Operation(summary="查询权益详情") public ApiResponse<BenefitView> getById(@PathVariable @Positive long id){return ApiResponse.success(service.getById(id));}
 @PostMapping("/{id}/claim") @Operation(summary="领取抽奖实物权益") public ApiResponse<ShippingOrderView> claim(@PathVariable @Positive long id,@Valid @RequestBody ClaimPhysicalBenefitCommand command){return ApiResponse.success(claims.claim(LoginContext.require().userId(),id,command));}
}

