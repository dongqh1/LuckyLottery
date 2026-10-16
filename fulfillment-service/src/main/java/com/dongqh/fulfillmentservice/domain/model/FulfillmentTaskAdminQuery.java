package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public class FulfillmentTaskAdminQuery {
    private long page = 1;
    private long size = 20;
    private FulfillmentType fulfillmentType;
    private FulfillmentStatus status;
    private Long targetUserId;
    private String sourceType;
    private String sourceId;

    public long getPage() { return page; }
    public void setPage(long page) {
        if (page < 1) throw new IllegalArgumentException("page必须大于0");
        this.page = page;
    }
    public long getSize() { return size; }
    public void setSize(long size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size必须为1到100");
        this.size = size;
    }
    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(FulfillmentType fulfillmentType) { this.fulfillmentType = fulfillmentType; }
    public FulfillmentStatus getStatus() { return status; }
    public void setStatus(FulfillmentStatus status) { this.status = status; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public FulfillmentTaskQuery toQuery() {
        return new FulfillmentTaskQuery(page, size, fulfillmentType, status, targetUserId, sourceType, sourceId);
    }
}
