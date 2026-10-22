package com.dongqh.lotteryservice.controller;

import com.dongqh.hmapi.dto.ShippingOrderDTO;
import com.dongqh.lotteryservice.domain.dto.benefit.ClaimPhysicalBenefitCommand;
import com.dongqh.lotteryservice.service.benefit.LotteryBenefitQueryService;
import com.dongqh.lotteryservice.service.benefit.LotteryPhysicalBenefitClaimService;
import com.dongqh.lotteryservice.domain.vo.benefit.BenefitView;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PageResponse;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/benefits")
public class LotteryBenefitController {
    private final LotteryBenefitQueryService queries;
    private final LotteryPhysicalBenefitClaimService claims;

    public LotteryBenefitController(LotteryBenefitQueryService queries,
                                    LotteryPhysicalBenefitClaimService claims) {
        this.queries = queries;
        this.claims = claims;
    }

    @GetMapping
    public R<PageResponse<BenefitView>> pageMine(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) BenefitStatus status,
            @RequestParam(required = false) PrizeType prizeType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(queries.pageMine(requireUserId(), page, size, status, prizeType, startDate, endDate));
    }

    @GetMapping("/{benefitId}")
    public R<BenefitView> getMine(@PathVariable("benefitId") @Positive long benefitId) {
        return R.ok(queries.getMine(requireUserId(), benefitId));
    }

    @PostMapping("/{benefitId}/claim")
    public R<ShippingOrderDTO> claim(
            @PathVariable("benefitId") @Positive long benefitId,
            @Valid @RequestBody ClaimPhysicalBenefitCommand command,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return R.ok(claims.claim(requireUserId(), benefitId, authorization, command));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}

