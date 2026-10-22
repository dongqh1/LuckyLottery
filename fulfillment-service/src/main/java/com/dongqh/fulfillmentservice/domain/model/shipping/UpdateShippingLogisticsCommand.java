package com.dongqh.fulfillmentservice.domain.model.shipping;

public record UpdateShippingLogisticsCommand(String carrierCode, String carrierName, String waybillNo) {
}
