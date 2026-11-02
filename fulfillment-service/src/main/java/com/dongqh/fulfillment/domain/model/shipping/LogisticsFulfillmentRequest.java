package com.dongqh.fulfillment.domain.model.shipping;

public record LogisticsFulfillmentRequest(
        String fulfillmentNo,
        long targetUserId,
        long shippingOrderId,
        String skuCode,
        int quantity,
        String receiver,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {

    public LogisticsFulfillmentRequest {
        fulfillmentNo = required(fulfillmentNo, "fulfillmentNo", 100);
        if (targetUserId <= 0 || shippingOrderId <= 0) {
            throw new IllegalArgumentException("用户与发货单 ID 必须为正数");
        }
        skuCode = required(skuCode, "skuCode", 64);
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity必须大于0");
        }
        receiver = sensitive(receiver, "receiver", 64);
        phone = sensitive(phone, "phone", 32);
        if (!phone.matches("1\\d{10}")) {
            throw new IllegalArgumentException("phone格式不合法");
        }
        province = sensitive(province, "province", 100);
        city = sensitive(city, "city", 100);
        district = sensitive(district, "district", 100);
        detail = sensitive(detail, "detail", 500);
        receiverMasked = masked(receiverMasked, "receiverMasked", 64);
        phoneMasked = sensitive(phoneMasked, "phoneMasked", 32);
        if (!phoneMasked.matches("\\d{3}\\*{4}\\d{4}")) {
            throw new IllegalArgumentException("phoneMasked必须脱敏");
        }
        regionMasked = masked(regionMasked, "regionMasked", 200);
    }

    @Override
    public String toString() {
        return "LogisticsFulfillmentRequest[REDACTED]";
    }

    private static String sensitive(String value, String field, int maxLength) {
        return required(value, field, maxLength);
    }

    private static String masked(String value, String field, int maxLength) {
        String normalized = required(value, field, maxLength);
        if (!normalized.contains("*")) {
            throw new IllegalArgumentException(field + "必须脱敏");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "长度不合法");
        }
        return normalized;
    }
}
