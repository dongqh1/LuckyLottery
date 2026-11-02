package com.dongqh.lottery.domain.po.activitycatalog;

import com.dongqh.lottery.enums.activityprize.PrizeType;

public class ActivityPrizeCatalogRow {
    private Long prizeId;
    private String prizeName;
    private PrizeType prizeType;
    private String imageUrl;
    private Integer sortOrder;
    private Long hmallItemId;

    public Long getPrizeId() { return prizeId; }
    public void setPrizeId(Long prizeId) { this.prizeId = prizeId; }
    public String getPrizeName() { return prizeName; }
    public void setPrizeName(String prizeName) { this.prizeName = prizeName; }
    public PrizeType getPrizeType() { return prizeType; }
    public void setPrizeType(PrizeType prizeType) { this.prizeType = prizeType; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getHmallItemId() { return hmallItemId; }
    public void setHmallItemId(Long hmallItemId) { this.hmallItemId = hmallItemId; }
}
