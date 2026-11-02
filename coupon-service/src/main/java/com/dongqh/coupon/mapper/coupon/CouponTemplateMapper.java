package com.dongqh.coupon.mapper.coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.coupon.domain.po.coupon.CouponTemplate;
import org.apache.ibatis.annotations.*; public interface CouponTemplateMapper extends BaseMapper<CouponTemplate>
{
    @Select("SELECT * FROM coupon_template WHERE id=#{id} FOR UPDATE")
    CouponTemplate lockById(@Param("id") long id);
}
