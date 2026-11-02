package com.dongqh.coupon.service.coupon.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.coupon.domain.dto.coupon.CouponCheckoutCommand;
import com.dongqh.coupon.domain.dto.coupon.CouponCheckoutItemCommand;
import com.dongqh.coupon.domain.dto.coupon.ReserveCouponCommand;
import com.dongqh.coupon.domain.po.coupon.CouponTemplate;
import com.dongqh.coupon.domain.po.coupon.UserCoupon;
import com.dongqh.coupon.enums.coupon.CouponErrorCode;
import com.dongqh.coupon.enums.coupon.CouponScopeType;
import com.dongqh.coupon.enums.coupon.UserCouponStatus;
import com.dongqh.coupon.domain.exception.CouponDomainException;
import com.dongqh.coupon.integration.CouponIntegrationService;
import com.dongqh.coupon.mapper.coupon.CouponTemplateMapper;
import com.dongqh.coupon.mapper.coupon.UserCouponMapper;
import com.dongqh.coupon.domain.model.coupon.CouponLockSnapshot;
import com.dongqh.coupon.service.coupon.CouponCheckoutService;
import com.dongqh.coupon.domain.vo.coupon.ApplicableCouponView;
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
    private final CouponIntegrationService integration;

    public CouponCheckoutServiceImpl(CouponTemplateMapper templates,
                                     UserCouponMapper coupons,
                                     CouponIntegrationService integration) {
        this.templates = templates;
        this.coupons = coupons;
        this.integration = integration;
    }

    @Override
    public List<ApplicableCouponView> listApplicable(long userId, CouponCheckoutCommand command) {
        // 计算购物车信息
        Basket basket = loadBasket(userId, command.items());

        //查询当前用户可以用的优惠券
        /*
        SELECT *
            FROM user_coupon
            WHERE user_id = 1001
              AND status = 'AVAILABLE'
              AND valid_from <= NOW()
              AND valid_until > NOW()
            ORDER BY valid_until ASC;
         */
        List<UserCoupon> mine = coupons.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, UserCouponStatus.AVAILABLE)
                .le(UserCoupon::getValidFrom, LocalDateTime.now())
                .gt(UserCoupon::getValidUntil, LocalDateTime.now())
                .orderByAsc(UserCoupon::getValidUntil));

        //
        if (mine.isEmpty()) {
            return List.of();
        }

        //批量查询优惠券模板
        Map<Long, CouponTemplate> byId = templates.selectBatchIds(mine.stream()
                        .map(UserCoupon::getTemplateId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));


        List<ApplicableCouponView> result = new ArrayList<>();
        //计算所有的优惠券能减多少
        for (UserCoupon coupon : mine) {
            CouponTemplate template = byId.get(coupon.getTemplateId());
            //判断优惠券适不适合
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
        /*
        SELECT *
            FROM user_coupon
            WHERE id = 501;
         */
        UserCoupon coupon = coupons.selectById(command.couponId());
        if (coupon == null || !Long.valueOf(userId).equals(coupon.getUserId())) {
            throw error(CouponErrorCode.COUPON_NOT_FOUND);
        }

        //再次校验优惠券是否适用
        CouponTemplate template = templates.selectById(coupon.getTemplateId());
        Basket basket = loadBasket(userId, command.items());
        Long discount = calculateDiscount(template, basket);
        if (discount == null) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
        //判断是否重复预占,幂等
        if (coupon.getStatus() == UserCouponStatus.LOCKED
                && command.orderNo().equals(coupon.getLockedOrderNo())) {
            return snapshot(coupon, template, discount);
        }

        //判断是否可用
        if (coupon.getStatus() != UserCouponStatus.AVAILABLE) {
            throw error(CouponErrorCode.COUPON_UNAVAILABLE);
        }
        /*
        UPDATE user_coupon
        SET
            status = 'LOCKED',
            locked_order_no = #{orderNo},
            version = version + 1
        WHERE id = #{id}
          AND user_id = #{userId}
          AND status = 'AVAILABLE'
          AND valid_from <= CURRENT_TIMESTAMP(3)
          AND valid_until > CURRENT_TIMESTAMP(3);
         */
        if (coupons.lock(coupon.getId(), userId, command.orderNo()) != 1) {
            //再查一下数据库
            UserCoupon winner = coupons.selectById(coupon.getId());
            // 如果是相同的并发请求，就返回第一个
            //如果已经被相同订单锁定，说明可能是相同订单的并发请求，可以返回成功。
            //
            //否则抛出 COUPON_STATE_CONFLICT。
            //
            //不过这个失败后再次读取的幂等兜底，在 MySQL 默认 REPEATABLE READ 隔离级别下，还要留意事务快照是否过旧；不能认为它在所有并发时序下都一定能读到胜出事务的新状态。
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
        //这个方法会查询优惠券，并验证它是否属于当前用户。
        UserCoupon coupon = ownedCoupon(userId, couponId);
        if (coupon.getStatus() == UserCouponStatus.USED && orderNo.equals(coupon.getUsedOrderNo())) {
            return;
        }
        //数据库更新
        /*
        UPDATE user_coupon
        SET
            status = 'USED',
            used_order_no = #{orderNo},
            version = version + 1
        WHERE id = #{id}
          AND status = 'LOCKED'
          AND locked_order_no = #{orderNo};
         */
        if (coupons.use(couponId, orderNo) != 1) {
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional
    public void release(long userId, long couponId, String orderNo) {
        //判断是不是属于该用户。
        UserCoupon coupon = ownedCoupon(userId, couponId);
        if (coupon.getStatus() == UserCouponStatus.AVAILABLE) {
            return;
        }
        if (coupons.release(couponId, orderNo) != 1) {
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
    }

    //获取购物车信息
    private Basket loadBasket(long userId, List<CouponCheckoutItemCommand> commands) {
        //
        if (commands == null || commands.isEmpty()) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
        //获取打折基准
        int basisPoints = integration.activeMembershipBenefit(userId)
                .map(CouponIntegrationService.MembershipBenefit::discountBasisPoints)
                .filter(value -> value != null && value > 0 && value <= FULL_PRICE_BASIS_POINTS)
                .orElse(FULL_PRICE_BASIS_POINTS);


        List<BasketLine> lines = new ArrayList<>();
        long grossTotal = 0L;
        //遍历购物车
        for (CouponCheckoutItemCommand command : commands) {
            if (command == null || command.itemId() <= 0 || command.quantity() <= 0) {
                throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
            }
            //获取商品的实际价格，库存等
            CouponIntegrationService.ItemSnapshot item = integration.getItem(command.itemId());
            if (item == null || item.price() == null || item.price() <= 0
                    || item.stock() == null || item.stock() < command.quantity()
                    || !Integer.valueOf(1).equals(item.status())) {
                throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
            }
            //计算购物车的金额
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

    private CouponDomainException error(CouponErrorCode code) {
        return new CouponDomainException(code);
    }

    private record BasketLine(CouponIntegrationService.ItemSnapshot item, long grossAmount) {
    }

    private record Basket(List<BasketLine> lines, int membershipBasisPoints, long discountedTotal) {
        boolean hasMembershipDiscount() {
            return membershipBasisPoints < FULL_PRICE_BASIS_POINTS;
        }
    }
}
