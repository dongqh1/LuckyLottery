package com.dongqh.lotteryservice.controller;

import com.dongqh.lotteryservice.draworder.dto.DrawCommand;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.draworder.service.LotteryDrawQueryService;
import com.dongqh.lotteryservice.draworder.service.LotteryDrawService;
import com.dongqh.lotteryservice.draworder.vo.DrawOrderView;
import com.dongqh.lotteryservice.draworder.vo.DrawRecordView;
import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.prizecatalog.vo.PageResponse;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.hmall.common.utils.UserContext;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/lottery")
public class LotteryDrawController {
    private final LotteryDrawService draws;
    private final LotteryDrawQueryService queries;

    public LotteryDrawController(LotteryDrawService draws, LotteryDrawQueryService queries) {
        this.draws = draws;
        this.queries = queries;
    }

    @PostMapping("/draws")
    @RequirePermission(ServicePermissionCodes.LOTTERY_DRAW)
    public R<DrawOrderView> draw(@Valid @RequestBody DrawCommand command) {
        return R.ok(draws.draw(command));
    }

    @GetMapping("/draws/{requestId}")
    @RequirePermission(ServicePermissionCodes.LOTTERY_DRAW_READ)
    public R<DrawOrderView> getDraw(@PathVariable("requestId") @NotBlank @Size(max = 64) String requestId) {
        return R.ok(draws.getByRequestId(requestId));
    }

    @GetMapping("/orders")
    @RequirePermission(ServicePermissionCodes.LOTTERY_ORDER_READ_ALL)
    public R<PageResponse<DrawOrderView>> pageOrders(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @Positive Long activityId,
            @RequestParam(required = false) DrawOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate drawDate) {
        return R.ok(queries.pageOrders(userId, page, size, activityId, status, drawDate));
    }

    @GetMapping("/my/orders")
    @RequirePermission(ServicePermissionCodes.LOTTERY_DRAW_READ)
    public R<PageResponse<DrawOrderView>> pageMyOrders(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @Positive Long activityId,
            @RequestParam(required = false) DrawOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate drawDate) {
        return R.ok(queries.pageMyOrders(requireUserId(), page, size, activityId, status, drawDate));
    }

    @GetMapping("/my/records")
    @RequirePermission(ServicePermissionCodes.LOTTERY_RECORD_READ)
    public R<PageResponse<DrawRecordView>> pageMyRecords(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @Positive Long activityId,
            @RequestParam(required = false) DrawResultType resultType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(queries.pageMyRecords(requireUserId(), page, size, activityId, resultType, startDate, endDate));
    }

    @GetMapping("/records")
    @RequirePermission(ServicePermissionCodes.LOTTERY_RECORD_READ)
    public R<PageResponse<DrawRecordView>> pageRecords(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @Positive Long activityId,
            @RequestParam(required = false) DrawResultType resultType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(queries.pageRecords(userId, page, size, activityId, resultType, startDate, endDate));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userId;
    }
}
