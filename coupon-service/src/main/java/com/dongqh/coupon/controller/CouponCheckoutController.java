package com.dongqh.coupon.controller;

import com.dongqh.coupon.domain.dto.coupon.CouponCheckoutCommand;
import com.dongqh.coupon.domain.dto.coupon.CouponOrderBindingCommand;
import com.dongqh.coupon.domain.dto.coupon.ReserveCouponCommand;
import com.dongqh.coupon.domain.model.coupon.CouponLockSnapshot;
import com.dongqh.coupon.service.coupon.CouponCheckoutService;
import com.dongqh.coupon.domain.vo.coupon.ApplicableCouponView;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/coupons/checkout")
public class CouponCheckoutController {
    private final CouponCheckoutService checkout;

    public CouponCheckoutController(CouponCheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/applicable")
    public R<List<ApplicableCouponView>> listApplicable(@Valid @RequestBody CouponCheckoutCommand command) {
        return R.ok(checkout.listApplicable(requireUserId(), command));
    }

    @PostMapping("/reservations")
    public R<CouponLockSnapshot> reserve(@Valid @RequestBody ReserveCouponCommand command) {
        return R.ok(checkout.reserve(requireUserId(), command));
    }

    @PutMapping("/reservations/{couponId}/used")
    public R<Void> use(@PathVariable("couponId") @Positive long couponId,
                       @Valid @RequestBody CouponOrderBindingCommand command) {
        checkout.use(requireUserId(), couponId, command.orderNo());
        return R.ok();
    }

    @DeleteMapping("/reservations/{couponId}")
    public R<Void> release(@PathVariable("couponId") @Positive long couponId,
                           @RequestParam String orderNo) {
        checkout.release(requireUserId(), couponId, orderNo);
        return R.ok();
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userId;
    }
}

