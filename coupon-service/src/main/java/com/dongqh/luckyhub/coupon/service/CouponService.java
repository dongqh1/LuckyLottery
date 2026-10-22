package com.dongqh.luckyhub.service.coupon;

import com.dongqh.luckyhub.domain.dto.coupon.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.domain.dto.coupon.IssueCouponCommand;
import com.dongqh.luckyhub.domain.model.coupon.CouponLockSnapshot;
import com.dongqh.luckyhub.domain.vo.coupon.CouponPageResponse;
import com.dongqh.luckyhub.domain.vo.coupon.CouponTemplateView;
import com.dongqh.luckyhub.domain.vo.coupon.UserCouponView;
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
