package com.dongqh.lotteryservice.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.lotteryservice.drawchance.service.DrawChanceReservationService;
import com.dongqh.lotteryservice.drawchance.vo.DrawChanceAccountView;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/lottery/chances")
public class DrawChanceController {
    private final DrawChanceReservationService chances;

    public DrawChanceController(DrawChanceReservationService chances) { this.chances = chances; }

    @GetMapping
    @RequirePermission(ServicePermissionCodes.LOTTERY_ACTIVITY_READ)
    public R<DrawChanceAccountView> mine() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return R.ok(chances.get(userId));
    }
}
