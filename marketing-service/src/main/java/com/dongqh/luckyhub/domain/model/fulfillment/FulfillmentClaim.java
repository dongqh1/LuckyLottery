package com.dongqh.luckyhub.domain.model.fulfillment;
import com.dongqh.luckyhub.enums.fulfillment.*;import java.time.LocalDateTime;
public record FulfillmentClaim(Long taskId,String fulfillmentNo,FulfillmentType fulfillmentType,Long targetUserId,String requestPayload,AttemptOperation operation,String leaseToken,LocalDateTime claimedAt,LocalDateTime leaseUntil){}
