package com.dongqh.lotteryservice.reward;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.lotteryservice.domain.model.prizecatalog.RewardPrizeTypeMapping;
import com.dongqh.lotteryservice.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public final class LotteryCurrentRewardResolver {
    private static final int ENABLED = 1;
    private static final long MAX_DISCRETE_QUANTITY = 100;

    private final PrizeCatalogMapper prizes;
    private final RewardDefinitionMapper definitions;
    private final ItemClient items;
    private final CouponClient coupons;
    private final MembershipClient memberships;

    public LotteryCurrentRewardResolver(PrizeCatalogMapper prizes, RewardDefinitionMapper definitions,
                                       ItemClient items, CouponClient coupons, MembershipClient memberships) {
        this.prizes = prizes;
        this.definitions = definitions;
        this.items = items;
        this.coupons = coupons;
        this.memberships = memberships;
    }

    public CurrentLotteryReward resolve(long prizeId) {
        CurrentLotteryReward current = resolveDefinition(prizeId);
        RewardDefinitionRow definition = current.definition();
        long targetId = definition.getTargetId() == null ? 0 : definition.getTargetId();
        return switch (definition.getRewardType()) {
            case PRODUCT -> withItem(current, targetId);
            case COUPON -> withCoupon(current, targetId);
            case MEMBERSHIP -> withMembership(current, targetId);
            case POINTS, DRAW_CHANCE -> current;
        };
    }

    public CurrentLotteryReward resolveDefinition(long prizeId) {
        if (prizeId <= 0) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        PrizeRow prize = prizes.selectById(prizeId);
        if (prize == null || !Integer.valueOf(ENABLED).equals(prize.getStatus())
                || prize.getRewardDefinitionId() == null) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        RewardDefinitionRow definition = definitions.selectById(prize.getRewardDefinitionId());
        if (definition == null || !Integer.valueOf(ENABLED).equals(definition.getStatus())
                || definition.getRewardType() == null || definition.getQuantity() == null
                || definition.getQuantity() <= 0) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        if (!RewardPrizeTypeMapping.matches(definition.getRewardType(), prize.getPrizeType())) {
            throw unavailable(RewardQuarantineReason.REWARD_TYPE_MISMATCH);
        }
        if (definition.getRewardType() != RewardType.POINTS
                && definition.getQuantity() > MAX_DISCRETE_QUANTITY) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        boolean targetRequired = definition.getRewardType() == RewardType.PRODUCT
                || definition.getRewardType() == RewardType.COUPON
                || definition.getRewardType() == RewardType.MEMBERSHIP;
        if (targetRequired != (definition.getTargetId() != null)
                || definition.getTargetId() != null && definition.getTargetId() <= 0) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        return new CurrentLotteryReward(prize, definition, null, null, null);
    }

    private CurrentLotteryReward withItem(CurrentLotteryReward current, long targetId) {
        List<ItemDTO> result = items.queryItemByIds(List.of(targetId));
        ItemDTO item = result == null ? null : result.stream()
                .filter(value -> value != null && Objects.equals(value.getId(), targetId))
                .findFirst().orElse(null);
        if (item == null || !Integer.valueOf(ENABLED).equals(item.getStatus())
                || item.getName() == null || item.getName().isBlank()) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        return new CurrentLotteryReward(current.prize(), current.definition(), item, null, null);
    }

    private CurrentLotteryReward withCoupon(CurrentLotteryReward current, long targetId) {
        CouponRewardTemplateDTO template = coupons.getRewardTemplate(targetId);
        if (template == null || !Objects.equals(template.id(), targetId)
                || !Integer.valueOf(ENABLED).equals(template.status())
                || template.templateCode() == null || template.templateCode().isBlank()) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        return new CurrentLotteryReward(current.prize(), current.definition(), null, template, null);
    }

    private CurrentLotteryReward withMembership(CurrentLotteryReward current, long targetId) {
        MembershipRewardProductDTO product = memberships.getRewardProduct(targetId);
        if (product == null || !Objects.equals(product.id(), targetId)
                || !Integer.valueOf(ENABLED).equals(product.status())
                || product.productCode() == null || product.productCode().isBlank()
                || product.membershipLevel() == null || product.membershipLevel().isBlank()
                || product.durationDays() == null || product.durationDays() <= 0) {
            throw unavailable(RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);
        }
        return new CurrentLotteryReward(current.prize(), current.definition(), null, null, product);
    }

    private RewardIdentityMismatchException unavailable(RewardQuarantineReason reason) {
        return new RewardIdentityMismatchException(reason);
    }
}
