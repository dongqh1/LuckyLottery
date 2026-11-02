package com.dongqh.coupon.service.coupon;

import com.dongqh.coupon.domain.dto.coupon.CreateCouponTemplateCommand;
import com.dongqh.coupon.domain.dto.coupon.IssueCouponCommand;
import com.dongqh.coupon.domain.model.coupon.CouponLockSnapshot;
import com.dongqh.coupon.domain.vo.coupon.CouponPageResponse;
import com.dongqh.coupon.domain.vo.coupon.CouponTemplateView;
import com.dongqh.coupon.domain.vo.coupon.UserCouponView;
import java.util.List;

public interface CouponService {

    CouponTemplateView createTemplate(CreateCouponTemplateCommand command);

    List<CouponTemplateView> searchTemplates(String keyword, int size);

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
