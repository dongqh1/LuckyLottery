package com.dongqh.lotteryservice.draworder.service;

import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.draworder.entity.DrawOrderRow;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.draworder.mapper.DrawOrderMapper;
import com.dongqh.lotteryservice.draworder.vo.DrawOrderView;
import com.dongqh.lotteryservice.draworder.vo.DrawRecordView;
import com.dongqh.lotteryservice.draworder.vo.DrawResultView;
import com.dongqh.lotteryservice.drawtransaction.entity.DrawRecordRow;
import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.drawtransaction.mapper.DrawRecordMapper;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;
import com.dongqh.lotteryservice.prizecatalog.vo.PageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LotteryDrawQueryService {
    private static final long MAX_PAGE = 1_000_000L;
    private static final LocalDate MYSQL_MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_SAFE_END_DATE = LocalDate.of(9999, 12, 30);

    private final DrawOrderMapper orders;
    private final DrawRecordMapper records;

    public LotteryDrawQueryService(DrawOrderMapper orders, DrawRecordMapper records) {
        this.orders = orders;
        this.records = records;
    }

    public PageResponse<DrawOrderView> pageMyOrders(long userId, long page, long size, Long activityId,
                                                     DrawOrderStatus status, LocalDate drawDate) {
        long offset = validatePage(page, size);
        if (userId <= 0 || (activityId != null && activityId <= 0)
                || (drawDate != null && (drawDate.isBefore(MYSQL_MIN_DATE)
                || drawDate.isAfter(MAX_SAFE_END_DATE.plusDays(1))))) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        List<DrawOrderRow> rows = orders.selectMinePage(userId, activityId, status, drawDate, size, offset);
        long total = orders.countMine(userId, activityId, status, drawDate);
        Map<Long, List<DrawRecordRow>> resultRows = rows.isEmpty() ? Map.of()
                : records.selectByOrderIds(rows.stream().map(DrawOrderRow::getId).toList()).stream()
                .collect(Collectors.groupingBy(DrawRecordRow::getOrderId));
        List<DrawOrderView> views = rows.stream().map(order -> new DrawOrderView(order.getId(),
                order.getRequestId(), order.getActivityId(), order.getDrawCount(), order.getDrawDate(),
                order.getStatus(), order.getFailReason(), order.getCompletedAt(),
                resultRows.getOrDefault(order.getId(), List.of()).stream().map(this::resultView).toList()))
                .toList();
        return orderPage(views, total, page, size);
    }

    public PageResponse<DrawOrderView> pageOrders(Long userId, long page, long size, Long activityId,
                                                  DrawOrderStatus status, LocalDate drawDate) {
        long offset = validatePage(page, size);
        if ((userId != null && userId <= 0) || (activityId != null && activityId <= 0)
                || (drawDate != null && (drawDate.isBefore(MYSQL_MIN_DATE)
                || drawDate.isAfter(MAX_SAFE_END_DATE.plusDays(1))))) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        List<DrawOrderRow> rows = orders.selectAdminPage(userId, activityId, status, drawDate, size, offset);
        long total = orders.countAdmin(userId, activityId, status, drawDate);
        Map<Long, List<DrawRecordRow>> resultRows = rows.isEmpty() ? Map.of()
                : records.selectByOrderIds(rows.stream().map(DrawOrderRow::getId).toList()).stream()
                .collect(Collectors.groupingBy(DrawRecordRow::getOrderId));
        List<DrawOrderView> views = rows.stream().map(order -> new DrawOrderView(order.getId(),
                order.getRequestId(), order.getActivityId(), order.getDrawCount(), order.getDrawDate(),
                order.getStatus(), order.getFailReason(), order.getCompletedAt(),
                resultRows.getOrDefault(order.getId(), List.of()).stream().map(this::resultView).toList()))
                .toList();
        return orderPage(views, total, page, size);
    }

    public PageResponse<DrawRecordView> pageMyRecords(long userId, long page, long size, Long activityId,
                                                       DrawResultType resultType, LocalDate startDate,
                                                       LocalDate endDate) {
        long offset = validatePage(page, size);
        if (userId <= 0 || (activityId != null && activityId <= 0)
                || invalidDate(startDate, false) || invalidDate(endDate, true)
                || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<DrawRecordRow> rows = records.selectMinePage(userId, activityId, resultType,
                startTime, endTime, size, offset);
        long total = records.countMine(userId, activityId, resultType, startTime, endTime);
        return recordPage(rows.stream().map(this::recordView).toList(), total, page, size);
    }

    public PageResponse<DrawRecordView> pageRecords(Long userId, long page, long size, Long activityId,
                                                     DrawResultType resultType, LocalDate startDate,
                                                     LocalDate endDate) {
        long offset = validatePage(page, size);
        if ((userId != null && userId <= 0) || (activityId != null && activityId <= 0)
                || invalidDate(startDate, false) || invalidDate(endDate, true)
                || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<DrawRecordRow> rows = records.selectAdminPage(userId, activityId, resultType,
                startTime, endTime, size, offset);
        long total = records.countAdmin(userId, activityId, resultType, startTime, endTime);
        return recordPage(rows.stream().map(this::recordView).toList(), total, page, size);
    }

    private boolean invalidDate(LocalDate date, boolean end) {
        if (date == null) return false;
        LocalDate max = end ? MAX_SAFE_END_DATE : MAX_SAFE_END_DATE.plusDays(1);
        return date.isBefore(MYSQL_MIN_DATE) || date.isAfter(max);
    }

    private long validatePage(long page, long size) {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > 100) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        try {
            return Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException exception) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
    }

    private DrawResultView resultView(DrawRecordRow row) {
        return new DrawResultView(row.getId(), row.getSequenceNo(), row.getResultType(), row.getPrizeId(),
                row.getPrizeName(), row.getPrizeType(), row.getPrizeImageUrl(), row.getBenefitId(),
                row.getRewardType() == RewardType.PRODUCT ? row.getRewardTargetId() : null);
    }

    private DrawRecordView recordView(DrawRecordRow row) {
        return new DrawRecordView(row.getId(), row.getOrderId(), row.getRequestId(), row.getSequenceNo(),
                row.getUserId(), row.getActivityId(), row.getResultType(), row.getPrizeId(), row.getPrizeName(),
                row.getPrizeType(), row.getPrizeImageUrl(), row.getBenefitId(), row.getDrawTime());
    }

    private PageResponse<DrawOrderView> orderPage(List<DrawOrderView> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size, pages(total, size));
    }

    private PageResponse<DrawRecordView> recordPage(List<DrawRecordView> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size, pages(total, size));
    }

    private long pages(long total, long size) {
        return total == 0 ? 0 : ((total - 1) / size) + 1;
    }
}
