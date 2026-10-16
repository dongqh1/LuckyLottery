package com.dongqh.luckyhub.domain.dto.fulfillment;
import jakarta.validation.constraints.Size;
public record FulfillmentOperationCommand(@Size(max=500) String note){}
