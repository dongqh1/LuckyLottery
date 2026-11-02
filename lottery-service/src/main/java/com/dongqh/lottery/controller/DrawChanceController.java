package com.dongqh.lottery.controller;

import com.dongqh.lottery.service.drawchance.DrawChanceReservationService;
import com.dongqh.lottery.domain.vo.drawchance.DrawChanceAccountView;
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
    public R<DrawChanceAccountView> mine() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return R.ok(chances.get(userId));
    }
}

