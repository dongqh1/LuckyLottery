package com.dongqh.lottery.controller;

import com.dongqh.lottery.domain.dto.activityprize.AddActivityPrizeCommand;
import com.dongqh.lottery.domain.dto.activityprize.UpdateActivityPrizeCommand;
import com.dongqh.lottery.service.activityprize.ActivityPrizeService;
import com.dongqh.lottery.domain.vo.activityprize.ActivityPrizeView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/activities/{activityId}/prizes")
public class ActivityPrizeAdminController {
    private final ActivityPrizeService activityPrizes;

    public ActivityPrizeAdminController(ActivityPrizeService activityPrizes) {
        this.activityPrizes = activityPrizes;
    }

    @PostMapping
    public R<ActivityPrizeView> add(@PathVariable("activityId") @Positive long activityId,
                                    @Valid @RequestBody AddActivityPrizeCommand command) {
        return R.ok(activityPrizes.add(activityId, command));
    }

    @GetMapping
    public R<List<ActivityPrizeView>> list(@PathVariable("activityId") @Positive long activityId) {
        return R.ok(activityPrizes.list(activityId));
    }

    @PutMapping("/{prizeId}")
    public R<ActivityPrizeView> update(@PathVariable("activityId") @Positive long activityId,
                                       @PathVariable("prizeId") @Positive long prizeId,
                                       @Valid @RequestBody UpdateActivityPrizeCommand command) {
        return R.ok(activityPrizes.update(activityId, prizeId, command));
    }

    @DeleteMapping("/{prizeId}")
    public R<Void> remove(@PathVariable("activityId") @Positive long activityId,
                          @PathVariable("prizeId") @Positive long prizeId) {
        activityPrizes.remove(activityId, prizeId);
        return R.ok();
    }
}

