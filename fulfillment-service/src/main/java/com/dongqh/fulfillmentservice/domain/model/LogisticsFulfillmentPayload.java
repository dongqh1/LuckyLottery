package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public record LogisticsFulfillmentPayload(
        long shippingOrderId,
        String skuCode,
        int quantity,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) implements FulfillmentPayload {

    public static final String REDACTED_REGION = "区域***";

    public LogisticsFulfillmentPayload {
        shippingOrderId = PayloadValidation.positive(shippingOrderId, "shippingOrderId");
        skuCode = PayloadValidation.required(skuCode, "skuCode");
        quantity = PayloadValidation.positive(quantity, "quantity");
        receiverMasked = PayloadValidation.required(receiverMasked, "receiverMasked");
        phoneMasked = PayloadValidation.required(phoneMasked, "phoneMasked");
        regionMasked = PayloadValidation.required(regionMasked, "regionMasked");
        if (!isMaskedReceiver(receiverMasked)
                || !phoneMasked.matches("\\d{3}\\*{4}\\d{4}")
                || !REDACTED_REGION.equals(regionMasked)) {
            throw new IllegalArgumentException("物流收件信息必须脱敏");
        }
    }

    private static boolean isMaskedReceiver(String value) {
        return "*".equals(value)
                || (value.endsWith("*") && value.codePointCount(0, value.length()) == 2);
    }

    @Override
    public FulfillmentType fulfillmentType() {
        return FulfillmentType.LOGISTICS;
    }
}
