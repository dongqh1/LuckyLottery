package com.dongqh.fulfillmentservice.persistence.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;

import java.util.Optional;

public interface ShippingOrderRepository {
    Optional<ShippingOrder> findById(long id);

    java.util.List<ShippingOrder> findByIds(java.util.Collection<Long> ids);

    Optional<ShippingOrder> lockById(long id);

    Optional<ShippingOrder> findByShippingNo(String shippingNo);

    Optional<ShippingOrder> lockByShippingNo(String shippingNo);

    java.util.List<Long> findAdminPage(ShippingStatus status, ShippingSourceType sourceType, String sourceId,
                                       Long targetUserId, String waybillNo, long limit, long offset);

    long countAdmin(ShippingStatus status, ShippingSourceType sourceType, String sourceId,
                    Long targetUserId, String waybillNo);

    Optional<ShippingOrder> findByFulfillmentNo(String fulfillmentNo);

    Optional<ShippingOrder> findBySource(ShippingSourceType sourceType, String sourceId);

    Optional<ShippingOrder> findByClaimRequestId(String claimRequestId);

    ShippingOrder insert(ShippingOrder order);

    boolean markFulfilling(long id, String fulfillmentNo);

    boolean updateLogistics(ShippingOrder expected, String carrierCode, String carrierName, String waybillNo);

    boolean updateProjection(ShippingOrder expected, ShippingOrder projected);

    java.util.List<Long> selectProjectionCandidateIdsAfter(long cursorId, int limit);

    java.util.List<Long> selectProjectionCandidateIdsBefore(long cursorId, int limit);

    long selectProjectionCandidateHighWaterMark();

    java.util.List<Long> selectProjectionCandidateIdsBetween(long cursorId, long highWaterMarkId, int limit);
}
