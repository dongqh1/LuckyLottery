package com.dongqh.membershipservice.service.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.membershipservice.domain.po.membership.MembershipProduct;
import com.dongqh.membershipservice.domain.po.membership.UserMembership;
import com.dongqh.membershipservice.mapper.membership.MembershipProductMapper;
import com.dongqh.membershipservice.mapper.membership.UserMembershipMapper;
import com.dongqh.membershipservice.domain.model.membership.ActiveMembershipBenefit;
import com.dongqh.membershipservice.domain.vo.membership.MembershipProductView;
import com.dongqh.membershipservice.domain.vo.membership.UserMembershipView;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.hmapi.dto.MembershipPurchaseSnapshotDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class MembershipQueryService {

    private final MembershipProductMapper products;
    private final UserMembershipMapper memberships;

    public MembershipQueryService(MembershipProductMapper products, UserMembershipMapper memberships) {
        this.products = products;
        this.memberships = memberships;
    }

    public List<MembershipProductView> listEnabledProducts() {
        return products.selectList(new LambdaQueryWrapper<MembershipProduct>()
                        .eq(MembershipProduct::getStatus, 1)
                        .eq(MembershipProduct::getSalesEnabled, 1)
                        .orderByAsc(MembershipProduct::getPriceCent)
                        .orderByAsc(MembershipProduct::getId)
                        .last("LIMIT 100"))
                .stream()
                .filter(product -> Integer.valueOf(1).equals(product.getStatus()))
                .filter(product -> Integer.valueOf(1).equals(product.getSalesEnabled()))
                .sorted(Comparator.comparing(MembershipProduct::getPriceCent)
                        .thenComparing(MembershipProduct::getId))
                .map(MembershipQueryService::view)
                .toList();
    }

    public MembershipRewardProductDTO getEnabledRewardProduct(long productId) {
        MembershipProduct product = products.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "会员产品不可用");
        }
        return new MembershipRewardProductDTO(product.getId(), product.getProductCode(),
                product.getMembershipLevel(), product.getDurationDays(), product.getStatus());
    }

    public MembershipPurchaseSnapshotDTO getEnabledPurchaseSnapshot(long productId) {
        MembershipProduct product = products.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())
                || product.getPriceCent() == null || product.getPriceCent() <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "会员购买产品不可用");
        }
        return new MembershipPurchaseSnapshotDTO(product.getId(), product.getProductCode(),
                product.getProductName(), product.getMembershipLevel(), product.getDurationDays(),
                product.getPriceCent(), product.getDiscountBasisPoints(), product.getDailyDrawBonus(),
                product.getPointsMultiplierBasisPoints());
    }

    public Optional<UserMembershipView> getMine(long userId) {
        return Optional.ofNullable(memberships.selectOne(new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getUserId, userId)))
                .map(MembershipQueryService::view);
    }

    public Optional<ActiveMembershipBenefit> activeBenefit(long userId) {
        return activeBenefit(userId, LocalDateTime.now());
    }

    public Optional<ActiveMembershipBenefit> activeBenefit(long userId, LocalDateTime now) {
        return Optional.ofNullable(memberships.selectOne(new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getUserId, userId)
                        .gt(UserMembership::getExpiresAt, now)))
                .map(membership -> new ActiveMembershipBenefit(
                        membership.getMembershipLevel(),
                        membership.getDiscountBasisPoints(),
                        membership.getDailyDrawBonus(),
                        membership.getPointsMultiplierBasisPoints(),
                        membership.getExpiresAt()));
    }

    private static MembershipProductView view(MembershipProduct product) {
        return new MembershipProductView(
                product.getId(), product.getProductCode(), product.getProductName(), product.getMembershipLevel(),
                product.getCardType(), product.getDurationDays(), product.getPriceCent(),
                product.getDiscountBasisPoints(), product.getDailyDrawBonus(),
                product.getPointsMultiplierBasisPoints(), product.getStatus());
    }

    private static UserMembershipView view(UserMembership membership) {
        return new UserMembershipView(
                membership.getId(), membership.getUserId(), membership.getMembershipLevel(),
                membership.getStartsAt(), membership.getExpiresAt(), membership.getDiscountBasisPoints(),
                membership.getDailyDrawBonus(), membership.getPointsMultiplierBasisPoints());
    }
}
