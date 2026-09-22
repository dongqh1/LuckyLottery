package com.dongqh.luckyhub.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOffer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PointsRedemptionOfferMapper extends BaseMapper<PointsRedemptionOffer> {

    @Update("""
            UPDATE points_redemption_offer
            SET available_stock = available_stock - #{quantity},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{offerId} AND status = 1 AND available_stock >= #{quantity}
            """)
    int deductStockIfAvailable(@Param("offerId") long offerId, @Param("quantity") int quantity);

    @Update("""
            UPDATE points_redemption_offer
            SET available_stock = available_stock + #{quantity},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{offerId}
              AND available_stock <= 1000000 - #{quantity}
            """)
    int restoreStockIfNoOverflow(@Param("offerId") long offerId, @Param("quantity") int quantity);
}
