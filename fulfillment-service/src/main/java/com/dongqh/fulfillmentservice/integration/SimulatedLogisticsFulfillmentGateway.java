package com.dongqh.fulfillmentservice.integration;

import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.service.port.LogisticsFulfillmentGateway;
import com.dongqh.fulfillmentservice.shipping.model.LogisticsFulfillmentRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class SimulatedLogisticsFulfillmentGateway implements LogisticsFulfillmentGateway {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SimulatedLogisticsFulfillmentGateway(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public FulfillmentGatewayResult execute(LogisticsFulfillmentRequest request) {
        if (request == null || request.fulfillmentNo() == null || request.fulfillmentNo().isBlank()) {
            return FulfillmentGatewayResult.permanent("INVALID_REQUEST", "物流请求无效");
        }
        String payload = serialize(new SafeLogisticsRequest(request.fulfillmentNo(), request.targetUserId(),
                request.shippingOrderId(), request.skuCode(), request.quantity(), request.receiverMasked(),
                request.phoneMasked(), request.regionMasked()));
        String fingerprint = digest(payload);
        FulfillmentGatewayResult existing = find(request.fulfillmentNo(), fingerprint);
        if (existing != null) {
            return existing;
        }
        String reference = "SIM-L-" + digest(request.fulfillmentNo()).substring(0, 24);
        try {
            jdbc.update("""
                    INSERT INTO sim_logistics_record
                        (fulfillment_no, request_fingerprint, request_payload, external_reference, status)
                    VALUES (?, ?, ?, ?, 'SUCCEEDED')
                    """, request.fulfillmentNo(), fingerprint, payload, reference);
            return FulfillmentGatewayResult.succeeded(reference);
        } catch (DuplicateKeyException race) {
            FulfillmentGatewayResult winner = find(request.fulfillmentNo(), fingerprint);
            return winner == null ? FulfillmentGatewayResult.unknown("SIM_RESULT_UNKNOWN", "模拟物流结果待查询") : winner;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentGatewayResult query(String fulfillmentNo) {
        if (fulfillmentNo == null || fulfillmentNo.isBlank()) {
            return FulfillmentGatewayResult.permanent("INVALID_REQUEST", "物流请求无效");
        }
        return jdbc.query("SELECT external_reference FROM sim_logistics_record WHERE fulfillment_no = ?",
                rs -> rs.next() ? FulfillmentGatewayResult.succeeded(rs.getString(1))
                        : FulfillmentGatewayResult.notFound(), fulfillmentNo.trim());
    }

    private FulfillmentGatewayResult find(String fulfillmentNo, String fingerprint) {
        return jdbc.query("SELECT request_fingerprint, external_reference FROM sim_logistics_record WHERE fulfillment_no = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return fingerprint.equals(rs.getString("request_fingerprint"))
                            ? FulfillmentGatewayResult.succeeded(rs.getString("external_reference"))
                            : FulfillmentGatewayResult.permanent("SIM_IDEMPOTENCY_CONFLICT", "物流幂等请求参数不一致");
                }, fulfillmentNo);
    }

    private String serialize(SafeLogisticsRequest request) {
        try {
            return json.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new IllegalStateException("模拟物流请求序列化失败", exception);
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private record SafeLogisticsRequest(String fulfillmentNo, Long targetUserId, long shippingOrderId,
                                        String skuCode, int quantity, String receiverMasked,
                                        String phoneMasked, String regionMasked) {
    }
}
