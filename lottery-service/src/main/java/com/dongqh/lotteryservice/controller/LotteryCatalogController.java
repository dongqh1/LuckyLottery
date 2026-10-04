package com.dongqh.lotteryservice.controller;

import com.dongqh.lotteryservice.activitycatalog.service.ActivityCatalogQueryService;
import com.dongqh.lotteryservice.activitycatalog.vo.ActivityCatalogView;
import com.dongqh.lotteryservice.activitycatalog.vo.ActivityPrizeCatalogView;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/lottery/activities")
@RequirePermission(ServicePermissionCodes.LOTTERY_ACTIVITY_READ)
public class LotteryCatalogController {

    private final ActivityCatalogQueryService catalog;

    public LotteryCatalogController(ActivityCatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public R<List<ActivityCatalogView>> listActivities() {
        requireUserId();
        return R.ok(catalog.listActivities());
    }

    @GetMapping("/{activityId}")
    public R<ActivityCatalogView> getActivity(@PathVariable("activityId") @Positive long activityId) {
        requireUserId();
        return R.ok(catalog.getActivity(activityId));
    }

    @GetMapping("/{activityId}/prizes")
    public R<List<ActivityPrizeCatalogView>> listPrizes(
            @PathVariable("activityId") @Positive long activityId
    ) {
        requireUserId();
        return R.ok(catalog.listPrizes(activityId));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
