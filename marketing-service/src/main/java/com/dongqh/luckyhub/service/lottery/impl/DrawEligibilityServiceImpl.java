package com.dongqh.luckyhub.service.lottery.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.domain.po.activity.MarketingActivity;
import com.dongqh.luckyhub.domain.po.activity.MarketingActivityPrize;
import com.dongqh.luckyhub.enums.activity.ActivityStatus;
import com.dongqh.luckyhub.mapper.activity.MarketingActivityMapper;
import com.dongqh.luckyhub.mapper.activity.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.config.lottery.LotteryProperties;
import com.dongqh.luckyhub.enums.lottery.LotteryErrorCode;
import com.dongqh.luckyhub.domain.model.lottery.DrawPrizeSnapshot;
import com.dongqh.luckyhub.service.lottery.DrawEligibilityService;
import com.dongqh.luckyhub.domain.po.prize.MarketingPrize;
import com.dongqh.luckyhub.mapper.prize.MarketingPrizeMapper;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;
import com.dongqh.luckyhub.service.reward.RewardSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DrawEligibilityServiceImpl implements DrawEligibilityService {
    private final MarketingActivityMapper activityMapper;
    private final MarketingActivityPrizeMapper relationMapper;
    private final MarketingPrizeMapper prizeMapper;
    private final RewardSnapshotService rewardSnapshots;
    private final Clock clock;

    @Autowired
    public DrawEligibilityServiceImpl(MarketingActivityMapper activityMapper,
                                      MarketingActivityPrizeMapper relationMapper,
                                      MarketingPrizeMapper prizeMapper,
                                      RewardSnapshotService rewardSnapshots,
                                      LotteryProperties properties) {
        this(activityMapper, relationMapper, prizeMapper, rewardSnapshots,
                Clock.system(properties.zoneId()));
    }

    DrawEligibilityServiceImpl(MarketingActivityMapper activityMapper,
                               MarketingActivityPrizeMapper relationMapper,
                               MarketingPrizeMapper prizeMapper,
                               RewardSnapshotService rewardSnapshots, Clock clock) {
        this.activityMapper = activityMapper;
        this.relationMapper = relationMapper;
        this.prizeMapper = prizeMapper;
        this.rewardSnapshots = rewardSnapshots;
        this.clock = clock;
    }

    @Override
    public EligibilitySnapshot load(long activityId) {
        MarketingActivity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException(LotteryErrorCode.ACTIVITY_NOT_FOUND);
        // MySQL DATETIME(3) stores milliseconds. Normalize before the synchronous response so
        // an idempotent retry read from MySQL is byte-for-byte equivalent to the first response.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (activity.getStatus() != ActivityStatus.RUNNING
                || activity.getStartTime() == null || activity.getEndTime() == null
                || now.isBefore(activity.getStartTime()) || !now.isBefore(activity.getEndTime())) {
            throw new BusinessException(LotteryErrorCode.ACTIVITY_NOT_AVAILABLE);
        }
        List<MarketingActivityPrize> relations = relationMapper.selectList(
                new LambdaQueryWrapper<MarketingActivityPrize>()
                        .eq(MarketingActivityPrize::getActivityId, activityId)
                        .orderByAsc(MarketingActivityPrize::getSortOrder)
                        .orderByAsc(MarketingActivityPrize::getId));
        List<Long> prizeIds = relations.stream().map(MarketingActivityPrize::getPrizeId).distinct().toList();
        Map<Long, MarketingPrize> prizes = prizeIds.isEmpty() ? Map.of()
                : prizeMapper.selectByIds(prizeIds).stream()
                .collect(Collectors.toMap(MarketingPrize::getId, Function.identity()));
        Map<Long, RewardSnapshot> resolvedRewards = rewardSnapshots.resolveForPrizes(
                prizeIds.stream().map(prizes::get).filter(Objects::nonNull).toList());
        List<DrawPrizeSnapshot> snapshots = relations.stream().map(relation -> {
            MarketingPrize prize = prizes.get(relation.getPrizeId());
            if (prize == null) throw new BusinessException(LotteryErrorCode.DRAW_WEIGHT_INVALID);
            return new DrawPrizeSnapshot(relation.getId(), prize.getId(), prize.getPrizeName(),
                    prize.getPrizeType(), prize.getImageUrl(), relation.getWeight(),
                    relation.getRemainingStock(), Integer.valueOf(1).equals(prize.getStatus()),
                    resolvedRewards.get(prize.getId()));
        }).toList();
        int noWinWeight = activity.getNoWinWeight() == null ? 0 : activity.getNoWinWeight();
        if (noWinWeight < 0 || (snapshots.isEmpty() && noWinWeight == 0)) {
            throw new BusinessException(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        return new EligibilitySnapshot(activityId, noWinWeight, snapshots, now);
    }
}
