package com.dongqh.luckyhub.service.lottery.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.domain.po.activity.MarketingActivity;
import com.dongqh.luckyhub.mapper.activity.MarketingActivityMapper;
import com.dongqh.luckyhub.mapper.activity.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.domain.po.benefit.UserBenefit;
import com.dongqh.luckyhub.mapper.benefit.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.lottery.DrawOrderQuery;
import com.dongqh.luckyhub.domain.dto.lottery.DrawRecordQuery;
import com.dongqh.luckyhub.domain.po.lottery.LotteryDrawOrder;
import com.dongqh.luckyhub.domain.po.lottery.LotteryDrawRecord;
import com.dongqh.luckyhub.enums.lottery.LotteryErrorCode;
import com.dongqh.luckyhub.mapper.lottery.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.mapper.lottery.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.service.lottery.LotteryQueryService;
import com.dongqh.luckyhub.domain.vo.lottery.*;
import com.dongqh.luckyhub.mapper.prize.MarketingPrizeMapper;
import com.dongqh.luckyhub.service.scope.DataScopeService;
import com.dongqh.luckyhub.service.scope.UserDataScope;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class LotteryQueryServiceImpl implements LotteryQueryService {
    private static final long MAX_PAGE = 1_000_000L;
    private static final LocalDate MYSQL_MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_SAFE_END_DATE = LocalDate.of(9999, 12, 30);
    private final MarketingActivityMapper activityMapper;
    private final MarketingActivityPrizeMapper activityPrizeMapper;
    private final MarketingPrizeMapper prizeMapper;
    private final LotteryDrawOrderMapper orderMapper;
    private final LotteryDrawRecordMapper recordMapper;
    private final UserBenefitMapper benefitMapper;
    private final DataScopeService dataScopeService;

    public LotteryQueryServiceImpl(MarketingActivityMapper activityMapper, LotteryDrawOrderMapper orderMapper,
                                   LotteryDrawRecordMapper recordMapper, UserBenefitMapper benefitMapper,
                                   DataScopeService dataScopeService) {
        this(activityMapper, null, null, orderMapper, recordMapper, benefitMapper, dataScopeService);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LotteryQueryServiceImpl(MarketingActivityMapper activityMapper,
                                   MarketingActivityPrizeMapper activityPrizeMapper,
                                   MarketingPrizeMapper prizeMapper,
                                   LotteryDrawOrderMapper orderMapper,
                                   LotteryDrawRecordMapper recordMapper, UserBenefitMapper benefitMapper,
                                   DataScopeService dataScopeService) {
        this.activityMapper = activityMapper;
        this.activityPrizeMapper = activityPrizeMapper;
        this.prizeMapper = prizeMapper;
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.benefitMapper = benefitMapper;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public List<LotteryActivityView> listActivities() {
        LocalDateTime now = LocalDateTime.now();
        return activityMapper.selectList(new LambdaQueryWrapper<MarketingActivity>()
                        .eq(MarketingActivity::getStatus, com.dongqh.luckyhub.enums.activity.ActivityStatus.RUNNING)
                        .le(MarketingActivity::getStartTime, now)
                        .ge(MarketingActivity::getEndTime, now)
                        .orderByAsc(MarketingActivity::getEndTime)
                        .orderByAsc(MarketingActivity::getId)
                        .last("LIMIT 100"))
                .stream().map(activity -> new LotteryActivityView(
                        activity.getId(), activity.getActivityName(), activity.getDescription(),
                        activity.getStatus(), activity.getStartTime(), activity.getEndTime()))
                .toList();
    }

    @Override
    public List<LotteryPrizeView> listPrizes(long activityId) {
        getActivity(activityId);
        MarketingActivityPrizeMapper requiredActivityPrizes = Objects.requireNonNull(activityPrizeMapper,
                "MarketingActivityPrizeMapper is required for prize queries");
        return List.copyOf(requiredActivityPrizes.selectVisiblePrizeViews(activityId));
    }

    @Override
    public LotteryActivityView getActivity(long activityId) {
        MarketingActivity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException(LotteryErrorCode.ACTIVITY_NOT_FOUND);
        return new LotteryActivityView(activity.getId(), activity.getActivityName(), activity.getDescription(),
                activity.getStatus(), activity.getStartTime(), activity.getEndTime());
    }

    @Override
    public DrawOrderView getDraw(String requestId) {
        LotteryDrawOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null) throw new BusinessException(LotteryErrorCode.DRAW_ACCESS_DENIED, "抽奖订单不存在或无权访问");
        dataScopeService.resolveUserScope(order.getUserId());
        return toOrderView(order, recordMapper.selectByOrderId(order.getId()));
    }

    @Override
    public PageResponse<DrawOrderView> pageOrders(DrawOrderQuery query) {
        validatePage(query.getPage(), query.getSize());
        validateMysqlDate(query.getDrawDate(), false);
        UserDataScope scope = dataScopeService.resolveUserScope(query.getUserId());
        LambdaQueryWrapper<LotteryDrawOrder> wrapper = new LambdaQueryWrapper<LotteryDrawOrder>()
                .eq(!scope.all(), LotteryDrawOrder::getUserId, scope.userId())
                .eq(query.getActivityId() != null, LotteryDrawOrder::getActivityId, query.getActivityId())
                .eq(query.getStatus() != null, LotteryDrawOrder::getStatus, query.getStatus())
                .eq(query.getDrawDate() != null, LotteryDrawOrder::getDrawDate, query.getDrawDate())
                .orderByDesc(LotteryDrawOrder::getCreatedAt).orderByDesc(LotteryDrawOrder::getId);
        Page<LotteryDrawOrder> page = orderMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        Map<Long, List<LotteryDrawRecord>> records = page.getRecords().isEmpty() ? Map.of()
                : recordMapper.selectByOrderIds(page.getRecords().stream().map(LotteryDrawOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(LotteryDrawRecord::getOrderId));
        List<DrawOrderView> views = page.getRecords().stream()
                .map(order -> toOrderView(order, records.getOrDefault(order.getId(), List.of()))).toList();
        return new PageResponse<>(views, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public PageResponse<DrawOrderView> pageMyOrders(long userId, DrawOrderQuery query) {
        query.setUserId(userId);
        return pageOrders(query);
    }

    @Override
    public PageResponse<DrawRecordView> pageRecords(DrawRecordQuery query) {
        validatePage(query.getPage(), query.getSize());
        validateMysqlDate(query.getStartDate(), false);
        validateMysqlDate(query.getEndDate(), true);
        if (query.getStartDate() != null && query.getEndDate() != null && query.getStartDate().isAfter(query.getEndDate())) {
            throw invalidQuery();
        }
        UserDataScope scope = dataScopeService.resolveUserScope(query.getUserId());
        LambdaQueryWrapper<LotteryDrawRecord> wrapper = new LambdaQueryWrapper<LotteryDrawRecord>()
                .eq(!scope.all(), LotteryDrawRecord::getUserId, scope.userId())
                .eq(query.getActivityId() != null, LotteryDrawRecord::getActivityId, query.getActivityId())
                .eq(query.getResultType() != null, LotteryDrawRecord::getResultType, query.getResultType())
                .ge(query.getStartDate() != null, LotteryDrawRecord::getDrawTime,
                        query.getStartDate() == null ? null : query.getStartDate().atStartOfDay())
                .lt(query.getEndDate() != null, LotteryDrawRecord::getDrawTime, endExclusive(query.getEndDate()))
                .orderByDesc(LotteryDrawRecord::getDrawTime).orderByDesc(LotteryDrawRecord::getId);
        Page<LotteryDrawRecord> page = recordMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        Map<Long, Long> benefits = benefitIds(page.getRecords());
        List<DrawRecordView> views = page.getRecords().stream().map(record -> toRecordView(record, benefits.get(record.getId()))).toList();
        return new PageResponse<>(views, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private void validatePage(long page, long size) {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > 100) throw invalidQuery();
        try { Math.multiplyExact(page - 1, size); } catch (ArithmeticException exception) { throw invalidQuery(); }
    }

    private void validateMysqlDate(LocalDate date, boolean safeEnd) {
        if (date == null) return;
        LocalDate maximum = safeEnd ? MAX_SAFE_END_DATE : MAX_SAFE_END_DATE.plusDays(1);
        if (date.isBefore(MYSQL_MIN_DATE) || date.isAfter(maximum)) throw invalidQuery();
    }

    private LocalDateTime endExclusive(LocalDate endDate) {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    private BusinessException invalidQuery() {
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER, "查询条件超出支持范围");
    }

    private Map<Long, Long> benefitIds(List<LotteryDrawRecord> records) {
        if (records.isEmpty()) return Map.of();
        List<Long> ids = records.stream().map(LotteryDrawRecord::getId).toList();
        return benefitMapper.selectList(new LambdaQueryWrapper<UserBenefit>().in(UserBenefit::getDrawRecordId, ids))
                .stream().collect(Collectors.toMap(UserBenefit::getDrawRecordId, UserBenefit::getId));
    }

    private DrawOrderView toOrderView(LotteryDrawOrder order, List<LotteryDrawRecord> records) {
        List<DrawResultView> results = records.stream().map(record -> new DrawResultView(record.getId(), record.getSequenceNo(),
                record.getResultType(), record.getPrizeId(), record.getPrizeName(), record.getPrizeType(),
                record.getPrizeImageUrl(), record.getBenefitId(),
                record.getRewardType() == com.dongqh.luckyhub.enums.reward.RewardType.PRODUCT
                        ? record.getRewardTargetId() : null)).toList();
        return new DrawOrderView(order.getId(), order.getRequestId(), order.getActivityId(), order.getDrawCount(),
                order.getDrawDate(), order.getStatus(), order.getFailReason(), order.getCompletedAt(), results);
    }

    private DrawRecordView toRecordView(LotteryDrawRecord record, Long benefitId) {
        return new DrawRecordView(record.getId(), record.getOrderId(), record.getRequestId(), record.getSequenceNo(),
                record.getUserId(), record.getActivityId(), record.getResultType(), record.getPrizeId(), record.getPrizeName(),
                record.getPrizeType(), record.getPrizeImageUrl(), benefitId, record.getDrawTime());
    }
}

