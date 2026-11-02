package com.dongqh.lottery.domain.po.activityprize;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lottery.enums.activityprize.PrizeLevel;
import com.dongqh.lottery.enums.activityprize.PrizeType;

@TableName("marketing_prize")
public class PrizeRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("prize_name")
    private String prizeName;
    @TableField("prize_type")
    private PrizeType prizeType;
    @TableField("prize_level")
    private PrizeLevel prizeLevel;
    @TableField("image_url")
    private String imageUrl;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPrizeName() { return prizeName; }
    public void setPrizeName(String prizeName) { this.prizeName = prizeName; }
    public PrizeType getPrizeType() { return prizeType; }
    public void setPrizeType(PrizeType prizeType) { this.prizeType = prizeType; }
    public PrizeLevel getPrizeLevel() { return prizeLevel; }
    public void setPrizeLevel(PrizeLevel prizeLevel) { this.prizeLevel = prizeLevel; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
