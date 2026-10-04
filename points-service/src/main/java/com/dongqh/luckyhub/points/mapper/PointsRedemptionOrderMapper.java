package com.dongqh.luckyhub.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PointsRedemptionOrderMapper extends BaseMapper<PointsRedemptionOrder> {

    @Insert("""
            INSERT IGNORE INTO points_redemption_order (
                redemption_no, user_id, sku_id, offer_id, hmall_item_id, quantity,
                unit_points, total_points, cash_price_cent, product_code, product_name,
                sku_code, sku_name, product_type, image_url, status, process_step,
                request_fingerprint, address_id, created_at, updated_at
            ) VALUES (
                #{order.redemptionNo}, #{order.userId}, #{order.skuId}, #{order.offerId},
                #{order.hmallItemId}, #{order.quantity}, #{order.unitPoints}, #{order.totalPoints},
                #{order.cashPriceCent}, #{order.productCode}, #{order.productName}, #{order.skuCode},
                #{order.skuName}, #{order.productType}, #{order.imageUrl}, 'PROCESSING', 'CLAIMED',
                #{order.requestFingerprint}, #{order.addressId}, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "order.id")
    int claim(@Param("order") PointsRedemptionOrder order);

    @Select("SELECT * FROM points_redemption_order WHERE redemption_no = #{redemptionNo} FOR UPDATE")
    PointsRedemptionOrder lockByRedemptionNo(@Param("redemptionNo") String redemptionNo);

    @Update("""
            UPDATE points_redemption_order SET process_step = #{target}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'PROCESSING' AND process_step = #{expected}
            """)
    int advanceStep(@Param("redemptionNo") String redemptionNo,
                    @Param("expected") String expected,
                    @Param("target") String target);

    @Update("""
            UPDATE points_redemption_order
            SET status = 'COMPLETED', process_step = 'COMPLETED', shipping_order_id = #{shippingOrderId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'PROCESSING'
              AND process_step = #{expectedStep}
            """)
    int complete(@Param("redemptionNo") String redemptionNo,
                 @Param("expectedStep") String expectedStep,
                 @Param("shippingOrderId") Long shippingOrderId);

    @Update("""
            UPDATE points_redemption_order
            SET reversal_no = #{reversalNo}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'COMPLETED'
              AND shipping_order_id IS NULL
              AND (reversal_no IS NULL OR reversal_no = #{reversalNo})
            """)
    int claimReversalNo(@Param("redemptionNo") String redemptionNo,
                        @Param("reversalNo") String reversalNo);

    @Update("""
            UPDATE points_redemption_order
            SET process_step = #{step}, original_error_code = #{errorCode}, failure_reason = #{reason},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'PROCESSING'
              AND process_step = #{expectedStep}
            """)
    int setCompensation(@Param("redemptionNo") String redemptionNo,
                        @Param("expectedStep") String expectedStep,
                        @Param("step") String step,
                        @Param("errorCode") String errorCode,
                        @Param("reason") String reason);

    @Update("""
            UPDATE points_redemption_order
            SET status = 'COMPENSATED', process_step = 'COMPENSATED',
                original_error_code = #{errorCode}, failure_reason = #{reason},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'PROCESSING'
              AND process_step = #{expectedStep}
            """)
    int compensate(@Param("redemptionNo") String redemptionNo,
                   @Param("expectedStep") String expectedStep,
                   @Param("errorCode") String errorCode,
                   @Param("reason") String reason);

    @Update("""
            UPDATE points_redemption_order
            SET shipping_order_id = #{shippingOrderId}, process_step = 'FULFILLMENT_ACCEPTED',
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'PROCESSING'
              AND process_step = 'SKU_CONFIRMED' AND shipping_order_id IS NULL
            """)
    int attachShippingOrder(@Param("redemptionNo") String redemptionNo,
                            @Param("shippingOrderId") Long shippingOrderId);

    @Update("""
            UPDATE points_redemption_order
            SET status = 'REVERSED', reversal_no = #{reversalNo}, failure_reason = #{reason},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'COMPLETED'
              AND reversal_no = #{reversalNo}
            """)
    int reverseCompleted(@Param("redemptionNo") String redemptionNo,
                         @Param("reversalNo") String reversalNo,
                         @Param("reason") String reason);
}
