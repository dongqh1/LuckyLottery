package com.dongqh.luckyhub.coupon.service;

import com.dongqh.luckyhub.coupon.dto.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.coupon.dto.IssueCouponCommand;
import com.dongqh.luckyhub.coupon.model.CouponLockSnapshot;
import com.dongqh.luckyhub.coupon.vo.CouponPageResponse;
import com.dongqh.luckyhub.coupon.vo.CouponTemplateView;
import com.dongqh.luckyhub.coupon.vo.UserCouponView;

public interface CouponService {
    CouponTemplateView createTemplate(CreateCouponTemplateCommand command);

    UserCouponView issue(IssueCouponCommand command);

    void issueReward(String fulfillmentNo, long userId, Long templateId,
                     String templateCode, int quantity);

    UserCouponView getMine(long userId, long couponId);

    CouponPageResponse<UserCouponView> pageMine(long userId, long page, long size);

    CouponLockSnapshot lockForOrder(long userId, long couponId, String orderNo, Long productId,
                                    long amountAfterMembership, boolean hasMembership);

    void useForOrder(long couponId, String orderNo);

    void releaseForOrder(long couponId, String orderNo);

    int expireAvailable();
}
