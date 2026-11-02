package com.dongqh.coupon.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The coupon service's small set of remote calls.
 *
 * Keeping these calls in one concrete class makes the business service easy to
 * read: coupon rules stay in coupon services, remote DTO conversion stays here.
 */
@Component
public class CouponIntegrationService {
    private static final int MIN_DISCOUNT_BASIS_POINTS = 1;
    private static final int MAX_DISCOUNT_BASIS_POINTS = 10_000;

    private final ItemClient items;
    private final MembershipClient memberships;
    private final UserClient users;

    public CouponIntegrationService(ItemClient items, MembershipClient memberships, UserClient users) {
        this.items = items;
        this.memberships = memberships;
        this.users = users;
    }

    public boolean isEnabledUser(long userId) {
        return Boolean.TRUE.equals(users.isEnabled(userId));
    }

    public ItemSnapshot getItem(long itemId) {
        List<ItemDTO> response = items.queryItemByIds(List.of(itemId));
        if (response == null) {
            return null;
        }
        List<ItemDTO> matches = response.stream()
                .filter(Objects::nonNull)
                .filter(item -> Long.valueOf(itemId).equals(item.getId()))
                .limit(2)
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        ItemDTO item = matches.get(0);
        return new ItemSnapshot(item.getId(), item.getName(), item.getPrice(), item.getStock(),
                item.getCategory(), item.getBrand(), item.getStatus());
    }

    public Optional<MembershipBenefit> activeMembershipBenefit(long userId) {
        MembershipBenefitDTO response = memberships.activeBenefit(userId);
        if (response == null) {
            throw new IllegalStateException("Membership service returned no benefit response");
        }
        if (!response.active()) {
            return Optional.empty();
        }
        Integer discountBasisPoints = response.discountBasisPoints();
        if (discountBasisPoints == null
                || discountBasisPoints < MIN_DISCOUNT_BASIS_POINTS
                || discountBasisPoints > MAX_DISCOUNT_BASIS_POINTS) {
            throw new IllegalStateException("Membership service returned an invalid active discount");
        }
        return Optional.of(new MembershipBenefit(discountBasisPoints));
    }

    public record ItemSnapshot(Long id, String name, Integer price, Integer stock,
                               String category, String brand, Integer status) {
        public boolean availableForReward() {
            return id != null && Integer.valueOf(1).equals(status) && stock != null && stock > 0;
        }
    }

    public record MembershipBenefit(Integer discountBasisPoints) {
    }
}
