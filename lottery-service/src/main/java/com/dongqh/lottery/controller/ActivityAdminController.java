package com.dongqh.lottery.controller;

import com.dongqh.lottery.domain.dto.activityprize.ActivityCommand;
import com.dongqh.lottery.domain.dto.activityprize.ActivityQuery;
import com.dongqh.lottery.service.activityprize.ActivityAdminService;
import com.dongqh.lottery.domain.vo.activityprize.ActivityView;
import com.dongqh.lottery.domain.vo.prizecatalog.PageResponse;
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
    public R<ActivityView> create(@Valid @RequestBody ActivityCommand command) {
        return R.ok(activities.create(command));
    }

    @GetMapping("/{id}")
    public R<ActivityView> get(@PathVariable("id") @Positive long id) { return R.ok(activities.get(id)); }

    @GetMapping
    public R<PageResponse<ActivityView>> page(@Valid @ModelAttribute ActivityQuery query) {
        return R.ok(activities.page(query));
    }

    @PutMapping("/{id}")
    public R<ActivityView> update(@PathVariable("id") @Positive long id,
                                  @Valid @RequestBody ActivityCommand command) {
        return R.ok(activities.update(id, command));
    }

    @PatchMapping("/{id}/publish")
    public R<ActivityView> publish(@PathVariable("id") @Positive long id) {
        return R.ok(activities.publish(id));
    }

    @PatchMapping("/{id}/disable")
    public R<Void> disable(@PathVariable("id") @Positive long id) {
        activities.disable(id);
        return R.ok();
    }

    @PatchMapping("/{id}/restore")
    public R<ActivityView> restore(@PathVariable("id") @Positive long id) {
        return R.ok(activities.restore(id));
    }
}

