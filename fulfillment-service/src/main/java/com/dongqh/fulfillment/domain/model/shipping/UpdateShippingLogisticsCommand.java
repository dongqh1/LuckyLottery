package com.dongqh.fulfillment.domain.model.shipping;

public record UpdateShippingLogisticsCommand(String carrierCode, String carrierName, String waybillNo) {
}
