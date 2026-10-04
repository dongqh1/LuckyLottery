package com.dongqh.lotteryservice.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.lotteryservice.activityprize.dto.ActivityCommand;
import com.dongqh.lotteryservice.activityprize.dto.ActivityQuery;
import com.dongqh.lotteryservice.activityprize.service.ActivityAdminService;
import com.dongqh.lotteryservice.activityprize.vo.ActivityView;
import com.dongqh.lotteryservice.prizecatalog.vo.PageResponse;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/activities")
public class ActivityAdminController {
    private final ActivityAdminService activities;

    public ActivityAdminController(ActivityAdminService activities) { this.activities = activities; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.ACTIVITY_CREATE)
    public R<ActivityView> create(@Valid @RequestBody ActivityCommand command) {
        return R.ok(activities.create(command));
    }

    @GetMapping("/{id}")
    @RequirePermission(ServicePermissionCodes.ACTIVITY_READ)
    public R<ActivityView> get(@PathVariable("id") @Positive long id) { return R.ok(activities.get(id)); }

    @GetMapping
    @RequirePermission(ServicePermissionCodes.ACTIVITY_READ)
    public R<PageResponse<ActivityView>> page(@Valid @ModelAttribute ActivityQuery query) {
        return R.ok(activities.page(query));
    }

    @PutMapping("/{id}")
    @RequirePermission(ServicePermissionCodes.ACTIVITY_UPDATE)
    public R<ActivityView> update(@PathVariable("id") @Positive long id,
                                  @Valid @RequestBody ActivityCommand command) {
        return R.ok(activities.update(id, command));
    }

    @PatchMapping("/{id}/publish")
    @RequirePermission(ServicePermissionCodes.ACTIVITY_PUBLISH)
    public R<ActivityView> publish(@PathVariable("id") @Positive long id) {
        return R.ok(activities.publish(id));
    }

    @PatchMapping("/{id}/disable")
    @RequirePermission(ServicePermissionCodes.ACTIVITY_DISABLE)
    public R<Void> disable(@PathVariable("id") @Positive long id) {
        activities.disable(id);
        return R.ok();
    }

    @PatchMapping("/{id}/restore")
    @RequirePermission(ServicePermissionCodes.ACTIVITY_RESTORE)
    public R<ActivityView> restore(@PathVariable("id") @Positive long id) {
        return R.ok(activities.restore(id));
    }
}
