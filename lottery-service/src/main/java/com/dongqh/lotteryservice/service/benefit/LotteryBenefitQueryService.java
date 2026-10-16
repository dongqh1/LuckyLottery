package com.dongqh.lotteryservice.service.benefit;

import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.dto.FulfillmentShippingOrderDTO;
import com.dongqh.hmapi.dto.FulfillmentTaskStatusDTO;
import com.dongqh.lotteryservice.domain.vo.benefit.BenefitView;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LotteryBenefitQueryService {
    private static final long MAX_PAGE = 1_000_000L;
    private static final LocalDate MYSQL_MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_SAFE_END_DATE = LocalDate.of(9999, 12, 30);

    private final UserBenefitMapper benefits;
    private final DrawRecordMapper records;
    private final FulfillmentClient fulfillment;

    public LotteryBenefitQueryService(UserBenefitMapper benefits, DrawRecordMapper records,
                                     FulfillmentClient fulfillment) {
        this.benefits = benefits;
        this.records = records;
        this.fulfillment = fulfillment;
    }

    public PageResponse<BenefitView> pageMine(long userId, long page, long size, BenefitStatus status,
                                               PrizeType prizeType, LocalDate startDate, LocalDate endDate) {
        long offset = validate(userId, page, size, startDate, endDate);
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<UserBenefitRow> rows = benefits.selectMinePage(userId, status, prizeType,
                startTime, endTime, size, offset);
        long total = benefits.countMine(userId, status, prizeType, startTime, endTime);
        List<BenefitView> views = toViews(rows);
        long pages = total == 0 ? 0 : (total - 1) / size + 1;
        return new PageResponse<>(views, total, page, size, pages);
    }

    public BenefitView getMine(long userId, long benefitId) {
        if (userId <= 0 || benefitId <= 0) {
            throw error(LotteryErrorCode.BENEFIT_QUERY_INVALID);
        }
        UserBenefitRow benefit = benefits.selectById(benefitId);
        if (benefit == null) {
            throw error(LotteryErrorCode.BENEFIT_NOT_FOUND);
        }
        if (!Objects.equals(benefit.getUserId(), userId)) {
            throw error(LotteryErrorCode.BENEFIT_ACCESS_DENIED);
        }
        return toViews(List.of(benefit)).get(0);
    }

    private List<BenefitView> toViews(List<UserBenefitRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> recordIds = rows.stream().map(UserBenefitRow::getDrawRecordId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, DrawRecordRow> recordById = recordIds.isEmpty() ? Map.of()
                : records.selectBatchIds(recordIds).stream()
                .collect(Collectors.toMap(DrawRecordRow::getId, Function.identity()));

        List<String> fulfillmentNos = rows.stream().map(UserBenefitRow::getFulfillmentNo)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        Map<String, String> taskStatusByNo = fulfillmentNos.isEmpty() ? Map.of()
                : fulfillment.getLotteryTaskStatuses(fulfillmentNos).stream()
                .collect(Collectors.toMap(FulfillmentTaskStatusDTO::fulfillmentNo,
                        FulfillmentTaskStatusDTO::status, (first, ignored) -> first));

        List<Long> shippingIds = rows.stream().map(UserBenefitRow::getShippingOrderId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, FulfillmentShippingOrderDTO> shippingById = shippingIds.isEmpty() ? Map.of()
                : fulfillment.getShippingOrdersByIds(shippingIds).stream()
                .filter(order -> order.id() != null)
                .collect(Collectors.toMap(FulfillmentShippingOrderDTO::id, Function.identity(),
                        (first, ignored) -> first));

        return rows.stream().map(benefit -> {
            DrawRecordRow record = benefit.getDrawRecordId() == null
                    ? null : recordById.get(benefit.getDrawRecordId());
            FulfillmentShippingOrderDTO shipping = benefit.getShippingOrderId() == null
                    ? null : shippingById.get(benefit.getShippingOrderId());
            String taskStatus = benefit.getFulfillmentNo() == null
                    ? null : taskStatusByNo.get(benefit.getFulfillmentNo());
            return new BenefitView(benefit.getId(), benefit.getDrawRecordId(), benefit.getUserId(),
                    benefit.getPrizeId(), benefit.getPrizeType(), record == null ? null : record.getPrizeName(),
                    record == null ? null : record.getPrizeImageUrl(), benefit.getQuantity(), benefit.getStatus(),
                    benefit.getObtainedAt(), benefit.getExpireAt(), benefit.getRewardDefinitionId(),
                    benefit.getRewardType(), benefit.getRewardQuantity(), benefit.getFulfillmentNo(),
                    taskStatus,
                    shipping == null ? null : shipping.shippingNo(),
                    shipping == null ? null : shipping.status());
        }).toList();
    }

    private long validate(long userId, long page, long size, LocalDate startDate, LocalDate endDate) {
        if (userId <= 0 || page < 1 || page > MAX_PAGE || size < 1 || size > 100
                || invalidDate(startDate, false) || invalidDate(endDate, true)
                || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
            throw error(LotteryErrorCode.BENEFIT_QUERY_INVALID);
        }
        try {
            return Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException exception) {
            throw error(LotteryErrorCode.BENEFIT_QUERY_INVALID);
        }
    }

    private boolean invalidDate(LocalDate date, boolean end) {
        if (date == null) return false;
        LocalDate max = end ? MAX_SAFE_END_DATE : MAX_SAFE_END_DATE.plusDays(1);
        return date.isBefore(MYSQL_MIN_DATE) || date.isAfter(max);
    }

    private LotteryDomainException error(LotteryErrorCode code) {
        return new LotteryDomainException(code);
    }
}
