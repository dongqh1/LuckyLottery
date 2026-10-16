package com.dongqh.lotteryservice.service.activityprize;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.lotteryservice.exception.activityprize.ActivityPrizeErrorCode;
import com.dongqh.lotteryservice.exception.activityprize.ActivityPrizeException;
import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;
import com.dongqh.lotteryservice.domain.dto.activityprize.ActivityCommand;
import com.dongqh.lotteryservice.domain.dto.activityprize.ActivityQuery;
import com.dongqh.lotteryservice.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lotteryservice.domain.po.activityprize.ActivityRow;
import com.dongqh.lotteryservice.domain.po.activityprize.PrizeRow;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityMapper;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lotteryservice.mapper.activityprize.PrizeMapper;
import com.dongqh.lotteryservice.domain.vo.activityprize.ActivityView;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PageResponse;
import com.hmall.common.utils.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityAdminService {
    private static final int ENABLED = 1;

    private final ActivityMapper activities;
    private final ActivityPrizeMapper relations;
    private final PrizeMapper prizes;
    private final Clock clock;

    public ActivityAdminService(ActivityMapper activities, ActivityPrizeMapper relations,
                                PrizeMapper prizes, com.dongqh.lotteryservice.config.LotteryProperties properties) {
        this.activities = activities;
        this.relations = relations;
        this.prizes = prizes;
        this.clock = Clock.system(properties.zoneId());
    }

    @Transactional
    public ActivityView create(ActivityCommand command) {
        validateTime(command);
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new IllegalArgumentException("当前用户不可用");
        ActivityRow row = new ActivityRow();
        apply(row, command);
        row.setStatus(ActivityStatus.DRAFT);
        row.setCreatedBy(userId);
        activities.insert(row);
        return view(activities.selectById(row.getId()));
    }

    public ActivityView get(long id) {
        return view(require(id));
    }

    public PageResponse<ActivityView> page(ActivityQuery query) {
        String name = normalize(query.getName());
        LambdaQueryWrapper<ActivityRow> wrapper = new LambdaQueryWrapper<ActivityRow>()
                .like(name != null, ActivityRow::getActivityName, name)
                .eq(query.getStatus() != null, ActivityRow::getStatus, query.getStatus())
                .orderByDesc(ActivityRow::getCreatedAt)
                .orderByDesc(ActivityRow::getId);
        Page<ActivityRow> result = activities.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResponse<>(result.getRecords().stream().map(ActivityAdminService::view).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Transactional
    public ActivityView update(long id, ActivityCommand command) {
        ActivityRow row = require(id);
        if (row.getStatus() == ActivityStatus.DISABLED || row.getStatus() == ActivityStatus.ENDED) {
            throw error(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        validateTime(command);
        if (row.getStatus() != ActivityStatus.DRAFT && !command.endTime().isAfter(now())) {
            throw error(ActivityPrizeErrorCode.ACTIVITY_TIME_INVALID);
        }
        apply(row, command);
        if (row.getStatus() != ActivityStatus.DRAFT) row.setStatus(publishedStatus(command.startTime()));
        activities.updateById(row);
        return view(activities.selectById(id));
    }

    @Transactional
    public ActivityView publish(long id) {
        ActivityRow row = require(id);
        if (row.getStatus() != ActivityStatus.DRAFT) throw error(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT);
        validateTime(row.getStartTime(), row.getEndTime());
        if (!row.getEndTime().isAfter(now())) throw error(ActivityPrizeErrorCode.ACTIVITY_TIME_INVALID);
        validatePrizes(row);
        row.setStatus(publishedStatus(row.getStartTime()));
        activities.updateById(row);
        return view(activities.selectById(id));
    }

    @Transactional
    public void disable(long id) {
        ActivityRow row = require(id);
        if (row.getStatus() == ActivityStatus.DISABLED) return;
        row.setStatus(ActivityStatus.DISABLED);
        activities.updateById(row);
    }

    @Transactional
    public ActivityView restore(long id) {
        ActivityRow row = require(id);
        if (row.getStatus() != ActivityStatus.DISABLED) throw error(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT);
        row.setStatus(ActivityStatus.DRAFT);
        activities.updateById(row);
        return view(activities.selectById(id));
    }

    private void validatePrizes(ActivityRow activity) {
        List<ActivityPrizeRow> configured = relations.selectList(new LambdaQueryWrapper<ActivityPrizeRow>()
                .eq(ActivityPrizeRow::getActivityId, activity.getId()));
        if (configured.isEmpty()) throw error(ActivityPrizeErrorCode.ACTIVITY_HAS_NO_PRIZE);
        Map<Long, PrizeRow> indexed = prizes.selectBatchIds(configured.stream().map(ActivityPrizeRow::getPrizeId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(PrizeRow::getId, Function.identity()));
        long totalWeight = activity.getNoWinWeight() == null ? 0 : activity.getNoWinWeight();
        boolean available = false;
        for (ActivityPrizeRow relation : configured) {
            PrizeRow prize = indexed.get(relation.getPrizeId());
            if (prize == null || !Integer.valueOf(ENABLED).equals(prize.getStatus())) {
                throw error(ActivityPrizeErrorCode.ACTIVITY_HAS_DISABLED_PRIZE);
            }
            if (relation.getWeight() == null || relation.getWeight() < 0 || relation.getTotalStock() == null
                    || relation.getRemainingStock() == null || relation.getRemainingStock() < 0
                    || relation.getRemainingStock() > relation.getTotalStock()) {
                throw error(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT);
            }
            totalWeight += relation.getWeight();
            available |= relation.getRemainingStock() > 0;
        }
        if (activity.getNoWinWeight() == null || activity.getNoWinWeight() < 0 || totalWeight <= 0) {
            throw error(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        if (!available) throw error(ActivityPrizeErrorCode.ACTIVITY_HAS_NO_AVAILABLE_STOCK);
    }

    private ActivityRow require(long id) {
        ActivityRow row = activities.selectById(id);
        if (row == null) throw error(ActivityPrizeErrorCode.ACTIVITY_NOT_FOUND);
        return row;
    }

    private void validateTime(ActivityCommand command) {
        if (command == null) throw error(ActivityPrizeErrorCode.ACTIVITY_TIME_INVALID);
        validateTime(command.startTime(), command.endTime());
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw error(ActivityPrizeErrorCode.ACTIVITY_TIME_INVALID);
        }
    }

    private void apply(ActivityRow row, ActivityCommand command) {
        row.setActivityName(command.activityName().trim());
        row.setDescription(normalize(command.description()));
        row.setStartTime(command.startTime());
        row.setEndTime(command.endTime());
        row.setNoWinWeight(command.noWinWeight());
    }

    private ActivityStatus publishedStatus(LocalDateTime start) {
        return now().isBefore(start) ? ActivityStatus.SCHEDULED : ActivityStatus.RUNNING;
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private ActivityPrizeException error(ActivityPrizeErrorCode code) { return new ActivityPrizeException(code); }

    private static ActivityView view(ActivityRow row) {
        return new ActivityView(row.getId(), row.getActivityName(), row.getDescription(), row.getStatus(),
                row.getStartTime(), row.getEndTime(), row.getDailyLimit(), row.getNoWinWeight(),
                row.getCreatedBy(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
