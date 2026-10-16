package com.dongqh.luckyhub.messaging.fulfillment.outbox;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("fulfillment_outbox")
public class FulfillmentOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String fulfillmentNo;
    private String eventType;
    private String payload;
    private FulfillmentOutboxStatus status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private String claimToken;
    private LocalDateTime claimUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    public static FulfillmentOutbox pending(
            String eventId, String fulfillmentNo, String eventType, String payload) {
        FulfillmentOutbox row = new FulfillmentOutbox();
        row.setEventId(eventId);
        row.setFulfillmentNo(fulfillmentNo);
        row.setEventType(eventType);
        row.setPayload(payload);
        row.setStatus(FulfillmentOutboxStatus.PENDING);
        row.setRetryCount(0);
        return row;
    }
}
