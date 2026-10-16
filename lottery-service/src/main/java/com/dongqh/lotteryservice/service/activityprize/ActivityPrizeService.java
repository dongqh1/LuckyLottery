package com.dongqh.lotteryservice.service.activityprize;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.lotteryservice.exception.activityprize.ActivityPrizeErrorCode;
import com.dongqh.lotteryservice.exception.activityprize.ActivityPrizeException;
import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;
import com.dongqh.lotteryservice.domain.dto.activityprize.AddActivityPrizeCommand;
import com.dongqh.lotteryservice.domain.dto.activityprize.UpdateActivityPrizeCommand;
import com.dongqh.lotteryservice.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lotteryservice.domain.po.activityprize.ActivityRow;
import com.dongqh.lotteryservice.domain.po.activityprize.PrizeRow;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityMapper;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lotteryservice.mapper.activityprize.PrizeMapper;
import com.dongqh.lotteryservice.domain.vo.activityprize.ActivityPrizeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityPrizeService {

    private final ActivityMapper activityMapper;
    private final ActivityPrizeMapper relationMapper;
    private final PrizeMapper prizeMapper;

    public ActivityPrizeService(ActivityMapper activityMapper, ActivityPrizeMapper relationMapper, PrizeMapper prizeMapper) {
        this.activityMapper = activityMapper;
        this.relationMapper = relationMapper;
        this.prizeMapper = prizeMapper;
    }

    @Transactional
    public ActivityPrizeView add(long activityId, AddActivityPrizeCommand command) {
        requireConfigurableActivity(activityId);
        PrizeRow prize = requireEnabledPrize(command.prizeId());
        if (findRelation(activityId, command.prizeId()) != null) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_PRIZE_DUPLICATE);
        }
        ActivityPrizeRow relation = new ActivityPrizeRow();
        relation.setActivityId(activityId);
        relation.setPrizeId(command.prizeId());
        relation.setWeight(command.weight());
        relation.setTotalStock(command.totalStock());
        relation.setRemainingStock(command.totalStock());
        relation.setSortOrder(command.sortOrder());
        relationMapper.insert(relation);
        return toView(relation, prize);
    }

    public List<ActivityPrizeView> list(long activityId) {
        requireActivity(activityId);
        List<ActivityPrizeRow> relations = relationMapper.selectList(
                new LambdaQueryWrapper<ActivityPrizeRow>()
                        .eq(ActivityPrizeRow::getActivityId, activityId)
                        .orderByAsc(ActivityPrizeRow::getSortOrder)
                        .orderByAsc(ActivityPrizeRow::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> prizeIds = relations.stream().map(ActivityPrizeRow::getPrizeId).toList();
        Map<Long, PrizeRow> prizes = prizeMapper.selectBatchIds(prizeIds).stream()
                .collect(Collectors.toMap(PrizeRow::getId, Function.identity()));
        return relations.stream().map(relation -> toView(relation, prizes.get(relation.getPrizeId()))).toList();
    }

    @Transactional
    public ActivityPrizeView update(long activityId, long prizeId, UpdateActivityPrizeCommand command) {
        requireConfigurableActivity(activityId);
        PrizeRow prize = requireEnabledPrize(prizeId);
        ActivityPrizeRow relation = requireRelation(activityId, prizeId);
        int consumedStock = relation.getTotalStock() - relation.getRemainingStock();
        if (command.totalStock() < consumedStock) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_STOCK_BELOW_CONSUMED);
        }
        relation.setWeight(command.weight());
        relation.setTotalStock(command.totalStock());
        relation.setRemainingStock(command.totalStock() - consumedStock);
        relation.setSortOrder(command.sortOrder());
        relationMapper.updateById(relation);
        return toView(relation, prize);
    }

    @Transactional
    public void remove(long activityId, long prizeId) {
        requireConfigurableActivity(activityId);
        ActivityPrizeRow relation = requireRelation(activityId, prizeId);
        relationMapper.deleteById(relation.getId());
    }

    private ActivityRow requireConfigurableActivity(long activityId) {
        ActivityRow activity = requireActivity(activityId);
        if (activity.getStatus() == ActivityStatus.DISABLED || activity.getStatus() == ActivityStatus.ENDED) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        return activity;
    }

    private ActivityRow requireActivity(long activityId) {
        ActivityRow activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private PrizeRow requireEnabledPrize(long prizeId) {
        PrizeRow prize = prizeMapper.selectById(prizeId);
        if (prize == null) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_PRIZE_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(prize.getStatus())) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_HAS_DISABLED_PRIZE);
        }
        return prize;
    }

    private ActivityPrizeRow requireRelation(long activityId, long prizeId) {
        ActivityPrizeRow relation = findRelation(activityId, prizeId);
        if (relation == null) {
            throw new ActivityPrizeException(ActivityPrizeErrorCode.ACTIVITY_PRIZE_NOT_FOUND);
        }
        return relation;
    }

    private ActivityPrizeRow findRelation(long activityId, long prizeId) {
        return relationMapper.selectOne(new LambdaQueryWrapper<ActivityPrizeRow>()
                .eq(ActivityPrizeRow::getActivityId, activityId)
                .eq(ActivityPrizeRow::getPrizeId, prizeId));
    }

    private ActivityPrizeView toView(ActivityPrizeRow relation, PrizeRow prize) {
        return new ActivityPrizeView(relation.getId(), relation.getActivityId(), relation.getPrizeId(),
                prize == null ? null : prize.getPrizeName(),
                prize == null ? null : prize.getPrizeType(),
                prize == null ? null : prize.getPrizeLevel(),
                prize == null ? null : prize.getImageUrl(),
                prize == null ? null : prize.getStatus(),
                relation.getWeight(), relation.getTotalStock(), relation.getRemainingStock(), relation.getSortOrder());
    }
}
