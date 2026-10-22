package com.dongqh.fulfillmentservice.controller.shipping;

import com.dongqh.fulfillmentservice.messaging.TradeOrderPaidDeadLetterReplayService;
import com.hmall.common.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/shipping/operations")
public class ShippingMessageRecoveryController {
    private final LogisticsAdminAuthorization authorization;
    private final TradeOrderPaidDeadLetterReplayService replay;

    public ShippingMessageRecoveryController(LogisticsAdminAuthorization authorization,
                                             TradeOrderPaidDeadLetterReplayService replay) {
        this.authorization = authorization;
        this.replay = replay;
    }

    @PostMapping("/replay-paid-events")
    public R<Integer> replayPaidEvents(@RequestParam(defaultValue = "100") int limit) {
        authorization.requireAdminId();
        if (limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
        }
        return R.ok(replay.replay(limit));
    }
}
