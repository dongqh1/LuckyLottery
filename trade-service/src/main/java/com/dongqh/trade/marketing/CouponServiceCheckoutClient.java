package com.dongqh.trade.marketing;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.dto.CouponCheckoutItemDTO;
import com.dongqh.hmapi.dto.CouponOrderBindingRequest;
import com.dongqh.hmapi.dto.CouponReservationDTO;
import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.hmapi.dto.ReserveCouponRequest;
import com.dongqh.trade.utils.AuthorizationContext;
import com.hmall.common.domain.R;
import com.hmall.common.exception.BadRequestException;
import feign.FeignException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "trade.coupon-checkout-service", name = "enabled", havingValue = "true")
public class CouponServiceCheckoutClient implements CouponCheckoutClient {
    private final CouponClient coupons;

    public CouponServiceCheckoutClient(CouponClient coupons) {
        this.coupons = coupons;
    }

    @Override
    public CouponReservationView reserve(Long couponId, Long orderId, List<OrderDetailDTO> details) {
        ReserveCouponRequest request = new ReserveCouponRequest(couponId, orderId.toString(), details.stream()
                .map(detail -> new CouponCheckoutItemDTO(detail.getItemId(), detail.getNum()))
                .toList());
        try {
            R<CouponReservationDTO> response = coupons.reserveForCheckout(AuthorizationContext.require(), request);
            CouponReservationDTO data = response == null || !response.success() ? null : response.getData();
            if (data == null || data.discountCent() == null
                    || data.discountCent() < Integer.MIN_VALUE || data.discountCent() > Integer.MAX_VALUE) {
                throw new BadRequestException("优惠券预占失败");
            }
            return new CouponReservationView(data.userCouponId(), data.templateId(), data.couponName(),
                    data.discountCent().intValue());
        } catch (FeignException exception) {
            throw new BadRequestException("优惠券不可用或不适用于当前订单");
        }
    }

    @Override
    public void use(Long couponId, Long orderId) {
        try {
            R<Void> response = coupons.useForCheckout(AuthorizationContext.require(), couponId,
                    new CouponOrderBindingRequest(orderId.toString()));
            if (response == null || !response.success()) throw new BadRequestException("优惠券核销失败");
        } catch (FeignException exception) {
            throw new BadRequestException("优惠券核销失败");
        }
    }

    @Override
    public void release(Long couponId, Long orderId) {
        try {
            R<Void> response = coupons.releaseForCheckout(AuthorizationContext.require(), couponId,
                    orderId.toString());
            if (response == null || !response.success()) throw new BadRequestException("优惠券释放失败");
        } catch (FeignException exception) {
            throw new BadRequestException("优惠券释放失败");
        }
    }
}
