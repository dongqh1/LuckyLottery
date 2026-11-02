package com.dongqh.coupon.mapper.coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.dongqh.coupon.domain.po.coupon.UserCoupon; import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
 @Insert("""
         INSERT IGNORE INTO user_coupon
             (coupon_no, template_id, user_id, status, valid_from, valid_until)
         SELECT CONCAT(template_code, '-', #{userId}), id, #{userId}, 'AVAILABLE', valid_from, valid_until
           FROM coupon_template
          WHERE template_code LIKE 'HMALL-CHECKOUT-%'
            AND status = 1
            AND valid_from <= CURRENT_TIMESTAMP(3)
            AND valid_until > CURRENT_TIMESTAMP(3)
         """)
 int issueHmallCheckoutCoupons(@Param("userId") long userId);

 @Insert("""
         INSERT IGNORE INTO coupon_issue_record
             (business_no, template_id, user_id, user_coupon_id)
         SELECT CONCAT('HMALL-AUTO-', coupon.coupon_no), coupon.template_id, coupon.user_id, coupon.id
           FROM user_coupon coupon
           JOIN coupon_template template ON template.id = coupon.template_id
          WHERE coupon.user_id = #{userId}
            AND template.template_code LIKE 'HMALL-CHECKOUT-%'
         """)
 int recordHmallCheckoutCouponIssues(@Param("userId") long userId);

 @Update("UPDATE user_coupon SET status='LOCKED',locked_order_no=#{orderNo},version=version+1 WHERE id=#{id} AND user_id=#{userId} AND status='AVAILABLE' AND valid_from<=CURRENT_TIMESTAMP(3) AND valid_until>CURRENT_TIMESTAMP(3)") int lock(@Param("id") long id,@Param("userId") long userId,@Param("orderNo") String orderNo);
 @Update("UPDATE user_coupon SET status='USED',used_order_no=#{orderNo},version=version+1 WHERE id=#{id} AND status='LOCKED' AND locked_order_no=#{orderNo}") int use(@Param("id") long id,@Param("orderNo") String orderNo);
 @Update("UPDATE user_coupon SET status='AVAILABLE',locked_order_no=NULL,version=version+1 WHERE id=#{id} AND status='LOCKED' AND locked_order_no=#{orderNo}") int release(@Param("id") long id,@Param("orderNo") String orderNo);
 @Update("UPDATE user_coupon SET status='EXPIRED',version=version+1 WHERE status='AVAILABLE' AND valid_until<=CURRENT_TIMESTAMP(3)") int expireAvailable();
 @Update("UPDATE user_coupon SET status='AVAILABLE',locked_order_no=NULL,version=version+1 WHERE status='LOCKED' AND updated_at<=#{cutoff}") int releaseExpiredLocks(@Param("cutoff") LocalDateTime cutoff);
}
