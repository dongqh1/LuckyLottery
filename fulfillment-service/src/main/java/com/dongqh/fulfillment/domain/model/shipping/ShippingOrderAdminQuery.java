package com.dongqh.fulfillment.domain.model.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;

public class ShippingOrderAdminQuery {
    private long page = 1;
    private long size = 20;
    private ShippingStatus status;
    private ShippingSourceType sourceType;
    private String sourceId;
    private Long targetUserId;
    private String waybillNo;

    public long getPage() { return page; }
    public void setPage(long page) {
        if (page < 1 || page > 1_000_000) throw new IllegalArgumentException("page参数无效");
        this.page = page;
    }
    public long getSize() { return size; }
    public void setSize(long size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size参数无效");
        this.size = size;
    }
    public ShippingStatus getStatus() { return status; }
    public void setStatus(ShippingStatus status) { this.status = status; }
    public ShippingSourceType getSourceType() { return sourceType; }
    public void setSourceType(ShippingSourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) {
        if (targetUserId != null && targetUserId <= 0) throw new IllegalArgumentException("targetUserId参数无效");
        this.targetUserId = targetUserId;
    }
    public String getWaybillNo() { return waybillNo; }
    public void setWaybillNo(String waybillNo) { this.waybillNo = waybillNo; }
}
