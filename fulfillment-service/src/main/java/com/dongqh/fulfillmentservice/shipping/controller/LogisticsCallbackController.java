package com.dongqh.fulfillmentservice.shipping.controller;

import com.dongqh.fulfillmentservice.shipping.model.LogisticsCallbackCommand;
import com.dongqh.fulfillmentservice.shipping.service.LogisticsCallbackService;
import com.hmall.common.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping/callbacks")
public class LogisticsCallbackController {

    private final LogisticsCallbackService callbacks;

    public LogisticsCallbackController(LogisticsCallbackService callbacks) {
        this.callbacks = callbacks;
    }

    @PostMapping("/logistics")
    public R<Void> callback(@RequestBody LogisticsCallbackCommand command) {
        callbacks.handle(command);
        return R.ok();
    }
}
