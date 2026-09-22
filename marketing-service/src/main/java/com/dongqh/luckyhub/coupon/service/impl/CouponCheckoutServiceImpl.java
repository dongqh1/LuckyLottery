package com.dongqh.luckyhub.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.coupon.dto.CouponCheckoutCommand;
import com.dongqh.luckyhub.coupon.dto.CouponCheckoutItemCommand;
import com.dongqh.luckyhub.coupon.dto.ReserveCouponCommand;
import com.dongqh.luckyhub.coupon.entity.CouponTemplate;
import com.dongqh.luckyhub.coupon.entity.UserCoupon;
import com.dongqh.luckyhub.coupon.enums.CouponErrorCode;
import com.dongqh.luckyhub.coupon.enums.CouponScopeType;
import com.dongqh.luckyhub.coupon.enums.UserCouponStatus;
import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.coupon.model.CouponLockSnapshot;
import com.dongqh.luckyhub.coupon.service.CouponCheckoutService;
import com.dongqh.luckyhub.coupon.vo.ApplicableCouponView;
import com.dongqh.luckyhub.integration.hmall.HmallCatalogClient;
import com.dongqh.luckyhub.integration.hmall.HmallItemView;
import com.dongqh.luckyhub.membership.model.ActiveMembershipBenefit;
import com.dongqh.luckyhub.membership.service.MembershipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CouponCheckoutServiceImpl implements CouponCheckoutService {
    private static final int FULL_PRICE_BASIS_POINTS = 10_000;

    private final CouponTemplateMapper templates;
    private final UserCouponMapper coupons;
    private final HmallCatalogClient catalog;
    private final MembershipService memberships;

    public CouponCheckoutServiceImpl(CouponTemplateMapper templates,
                                     UserCouponMapper coupons,
                                     HmallCatalogClient catalog,
                                     MembershipService memberships) {
        this.templates = templates;
        this.coupons = coupons;
        this.catalog = catalog;
        this.memberships = memberships;
    }

    @Override
    public List<ApplicableCouponView> listApplicable(long userId, CouponCheckoutCommand command) {
        Basket basket = loadBasket(userId, command.items());
        List<UserCoupon> mine = coupons.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, UserCouponStatus.AVAILABLE)
                .le(UserCoupon::getValidFrom, LocalDateTime.now())
                .gt(UserCoupon::getValidUntil, LocalDateTime.now())
                .orderByAsc(UserCoupon::getValidUntil));
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, CouponTemplate> byId = templates.selectBatchIds(mine.stream()
                        .map(UserCoupon::getTemplateId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
        List<ApplicableCouponView> result = new ArrayList<>();
        for (UserCoupon coupon : mine) {
            CouponTemplate template = byId.get(coupon.getTemplateId());
            Long discount = calculateDiscount(template, basket);
            if (discount != null) {
                result.add(new ApplicableCouponView(
                        coupon.getId().toString(), template.getTemplateName(), template.getThresholdCent(), discount,
                        template.getScopeType(), scopeValue(template), coupon.getValidUntil(),
                        Boolean.TRUE.equals(template.getStackableWithMembership())));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public CouponLockSnapshot reserve(long userId, ReserveCouponCommand command) {
        UserCoupon coupon = coupons.selectById(command.couponId());
        if (coupon == null || !Long.valueOf(userId).equals(coupon.getUserId())) {
            throw error(CouponErrorCode.COUPON_NOT_FOUND);
        }
        CouponTemplate template = templates.selectById(coupon.getTemplateId());
        Basket basket = loadBasket(userId, command.items());
        Long discount = calculateDiscount(template, basket);
        if (discount == null) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
        if (coupon.getStatus() == UserCouponStatus.LOCKED
                && command.orderNo().equals(coupon.getLockedOrderNo())) {
            return snapshot(coupon, template, discount);
        }
        if (coupon.getStatus() != UserCouponStatus.AVAILABLE) {
            throw error(CouponErrorCode.COUPON_UNAVAILABLE);
        }
        if (coupons.lock(coupon.getId(), userId, command.orderNo()) != 1) {
            UserCoupon winner = coupons.selectById(coupon.getId());
            if (winner != null && winner.getStatus() == UserCouponStatus.LOCKED
                    && command.orderNo().equals(winner.getLockedOrderNo())) {
                return snapshot(winner, template, discount);
            }
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
        return snapshot(coupon, template, discount);
    }

    @Override
    @Transactional
    public void use(long userId, long couponId, String orderNo) {
        UserCoupon coupon = ownedCoupon(userId, couponId);
        if (coupon.getStatus() == UserCouponStatus.USED && orderNo.equals(coupon.getUsedOrderNo())) {
            return;
        }
        if (coupons.use(couponId, orderNo) != 1) {
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional
    public void release(long userId, long couponId, String orderNo) {
        UserCoupon coupon = ownedCoupon(userId, couponId);
        if (coupon.getStatus() == UserCouponStatus.AVAILABLE) {
            return;
        }
        if (coupons.release(couponId, orderNo) != 1) {
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
    }

    private Basket loadBasket(long userId, List<CouponCheckoutItemCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
        int basisPoints = memberships.activeBenefit(userId)
                .map(ActiveMembershipBenefit::discountBasisPoints)
                .filter(value -> value != null && value > 0 && value <= FULL_PRICE_BASIS_POINTS)
                .orElse(FULL_PRICE_BASIS_POINTS);
        List<BasketLine> lines = new ArrayList<>();
        long grossTotal = 0L;
        for (CouponCheckoutItemCommand command : commands) {
            if (command == null || command.itemId() <= 0 || command.quantity() <= 0) {
                throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
            }
            HmallItemView item = catalog.getItem(command.itemId());
            if (item == null || item.price() == null || item.price() <= 0
                    || item.stock() == null || item.stock() < command.quantity()
                    || !Integer.valueOf(1).equals(item.status())) {
                throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
            }
            long gross = Math.multiplyExact(item.price().longValue(), command.quantity());
            grossTotal = Math.addExact(grossTotal, gross);
            lines.add(new BasketLine(item, gross));
        }
        return new Basket(lines, basisPoints, discounted(grossTotal, basisPoints));
    }

    private Long calculateDiscount(CouponTemplate template, Basket basket) {
        LocalDateTime now = LocalDateTime.now();
        if (template == null || template.getStatus() == null || template.getStatus() != 1
                || template.getValidFrom() == null || template.getValidUntil() == null
                || now.isBefore(template.getValidFrom()) || !now.isBefore(template.getValidUntil())
                || template.getScopeType() == null || template.getApplicableProductId() != null
                || template.getDiscountCent() == null || template.getDiscountCent() <= 0
                || template.getThresholdCent() == null
                || (basket.hasMembershipDiscount() && !Boolean.TRUE.equals(template.getStackableWithMembership()))) {
            return null;
        }
        long eligibleAmount = eligibleAmount(template, basket);
        if (eligibleAmount <= 0 || eligibleAmount < template.getThresholdCent()) {
            return null;
        }
        return Math.min(template.getDiscountCent(), eligibleAmount);
    }

    private long eligibleAmount(CouponTemplate template, Basket basket) {
        if (template.getScopeType() == CouponScopeType.ALL) {
            return basket.discountedTotal();
        }
        long eligibleGross = 0L;
        for (BasketLine line : basket.lines()) {
            boolean matches = switch (template.getScopeType()) {
                case ITEM -> template.getHmallItemId() != null
                        && template.getHmallItemId().equals(line.item().id());
                case CATEGORY -> sameText(template.getHmallCategory(), line.item().category());
                case BRAND -> sameText(template.getHmallBrand(), line.item().brand());
                case ALL -> true;
            };
            if (matches) {
                eligibleGross = Math.addExact(eligibleGross, line.grossAmount());
            }
        }
        return discounted(eligibleGross, basket.membershipBasisPoints());
    }

    private long discounted(long amount, int basisPoints) {
        return (Math.multiplyExact(amount, basisPoints) + FULL_PRICE_BASIS_POINTS - 1)
                / FULL_PRICE_BASIS_POINTS;
    }

    private boolean sameText(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private String scopeValue(CouponTemplate template) {
        return switch (template.getScopeType()) {
            case ALL -> "全部商品";
            case ITEM -> String.valueOf(template.getHmallItemId());
            case CATEGORY -> template.getHmallCategory();
            case BRAND -> template.getHmallBrand();
        };
    }

    private CouponLockSnapshot snapshot(UserCoupon coupon, CouponTemplate template, long discount) {
        return new CouponLockSnapshot(coupon.getId(), template.getId(), template.getTemplateName(), discount);
    }

    private UserCoupon ownedCoupon(long userId, long couponId) {
        UserCoupon coupon = coupons.selectById(couponId);
        if (coupon == null || !Long.valueOf(userId).equals(coupon.getUserId())) {
            throw error(CouponErrorCode.COUPON_NOT_FOUND);
        }
        return coupon;
    }

    private BusinessException error(CouponErrorCode code) {
        return new BusinessException(code);
    }

    private record BasketLine(HmallItemView item, long grossAmount) {
    }

    private record Basket(List<BasketLine> lines, int membershipBasisPoints, long discountedTotal) {
        boolean hasMembershipDiscount() {
            return membershipBasisPoints < FULL_PRICE_BASIS_POINTS;
        }
    }
}
