package com.dongqh.lotteryservice.activitycatalog.service;

import com.dongqh.lotteryservice.activitycatalog.entity.ActivityCatalogRow;
import com.dongqh.lotteryservice.activitycatalog.entity.ActivityPrizeCatalogRow;
import com.dongqh.lotteryservice.activitycatalog.vo.ActivityCatalogView;
import com.dongqh.lotteryservice.activitycatalog.vo.ActivityPrizeCatalogView;
import com.dongqh.lotteryservice.activityprize.mapper.ActivityCatalogQueryMapper;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityCatalogQueryService {
    private final ActivityCatalogQueryMapper mapper;

    public ActivityCatalogQueryService(ActivityCatalogQueryMapper mapper) {
        this.mapper = mapper;
    }

    public List<ActivityCatalogView> listActivities() {
        LocalDateTime now = LocalDateTime.now();
        return mapper.listActivities(now).stream().map(ActivityCatalogQueryService::toActivityView).toList();
    }

    public ActivityCatalogView getActivity(long activityId) {
        ActivityCatalogRow activity = mapper.findActivityById(activityId);
        if (activity == null) {
            throw new LotteryDomainException(LotteryErrorCode.ACTIVITY_NOT_FOUND);
        }
        return toActivityView(activity);
    }

    public List<ActivityPrizeCatalogView> listPrizes(long activityId) {
        getActivity(activityId);
        return mapper.listVisiblePrizes(activityId).stream().map(ActivityCatalogQueryService::toPrizeView).toList();
    }

    private static ActivityCatalogView toActivityView(ActivityCatalogRow activity) {
        return new ActivityCatalogView(activity.getId(), activity.getActivityName(), activity.getDescription(),
                activity.getStatus(), activity.getStartTime(), activity.getEndTime(), activity.getDailyLimit());
    }

    private static ActivityPrizeCatalogView toPrizeView(ActivityPrizeCatalogRow prize) {
        return new ActivityPrizeCatalogView(prize.getPrizeId(), prize.getPrizeName(), prize.getPrizeType(),
                prize.getImageUrl(), prize.getSortOrder(), prize.getHmallItemId());
    }
}
