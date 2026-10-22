package com.dongqh.lotteryservice.service.draweligibility;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.domain.model.draweligibility.DrawEligibility;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawPrizeCandidate;
import com.dongqh.lotteryservice.domain.model.prizecatalog.RewardPrizeTypeMapping;
import com.dongqh.lotteryservice.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lotteryservice.domain.po.activityprize.ActivityRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityMapper;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DrawEligibilityService {
    private static final int ENABLED = 1;
    private static final long MAX_DISCRETE_QUANTITY = 100;

    private final ActivityMapper activities;
    private final ActivityPrizeMapper activityPrizes;
    private final PrizeCatalogMapper prizes;
    private final RewardDefinitionMapper rewardDefinitions;
    private final ItemClient items;
    private final CouponClient coupons;
    private final MembershipClient memberships;
    private final Clock clock;

    public DrawEligibilityService(ActivityMapper activities, ActivityPrizeMapper activityPrizes,
                                  PrizeCatalogMapper prizes, RewardDefinitionMapper rewardDefinitions,
                                  ItemClient items, CouponClient coupons, MembershipClient memberships,
                                  LotteryProperties properties) {
        this.activities = activities;
        this.activityPrizes = activityPrizes;
        this.prizes = prizes;
        this.rewardDefinitions = rewardDefinitions;
        this.items = items;
        this.coupons = coupons;
        this.memberships = memberships;
        this.clock = Clock.system(properties.zoneId());
    }

    public DrawEligibility load(long activityId) {
        ActivityRow activity = activities.selectById(activityId);
        if (activity == null) throw error(LotteryErrorCode.ACTIVITY_NOT_FOUND);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (activity.getStatus() != ActivityStatus.RUNNING || activity.getStartTime() == null
                || activity.getEndTime() == null || now.isBefore(activity.getStartTime())
                || !now.isBefore(activity.getEndTime())) {
            throw error(LotteryErrorCode.ACTIVITY_NOT_AVAILABLE);
        }

        List<ActivityPrizeRow> relations = activityPrizes.selectList(new LambdaQueryWrapper<ActivityPrizeRow>()
                .eq(ActivityPrizeRow::getActivityId, activityId)
                .orderByAsc(ActivityPrizeRow::getSortOrder)
                .orderByAsc(ActivityPrizeRow::getId));
        List<Long> prizeIds = relations.stream().map(ActivityPrizeRow::getPrizeId).distinct().toList();
        Map<Long, PrizeRow> prizeById = prizeIds.isEmpty() ? Map.of()
                : byId(prizes.selectByIds(prizeIds), PrizeRow::getId);
        List<Long> definitionIds = prizeById.values().stream().map(PrizeRow::getRewardDefinitionId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, RewardDefinitionRow> definitions = definitionIds.isEmpty() ? Map.of()
                : byId(rewardDefinitions.selectByIds(definitionIds), RewardDefinitionRow::getId);
        TargetIndex targets = loadTargets(definitions.values().stream().toList());

        List<DrawPrizeCandidate> candidates = relations.stream().map(relation -> {
            PrizeRow prize = prizeById.get(relation.getPrizeId());
            if (prize == null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
            RewardDefinitionRow reward = reward(prize, definitions, targets);
            return new DrawPrizeCandidate(relation.getId(), prize.getId(), prize.getPrizeName(),
                    prize.getPrizeType(), prize.getImageUrl(), relation.getWeight(),
                    relation.getRemainingStock(), Integer.valueOf(ENABLED).equals(prize.getStatus()),
                    reward.getId(), reward.getRewardType(), reward.getTargetId(), reward.getQuantity());
        }).toList();

        int noWinWeight = activity.getNoWinWeight() == null ? 0 : activity.getNoWinWeight();
        if (noWinWeight < 0 || (candidates.isEmpty() && noWinWeight == 0)) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        return new DrawEligibility(activityId, noWinWeight, candidates, now);
    }

    private TargetIndex loadTargets(List<RewardDefinitionRow> definitions) {
        List<Long> couponIds = targetIds(definitions, RewardType.COUPON);
        List<Long> membershipIds = targetIds(definitions, RewardType.MEMBERSHIP);
        List<Long> itemIds = targetIds(definitions, RewardType.PRODUCT);
        Map<Long, CouponRewardTemplateDTO> couponById = couponIds.stream().collect(Collectors.toMap(
                Function.identity(), coupons::getRewardTemplate));
        Map<Long, MembershipRewardProductDTO> membershipById = membershipIds.stream().collect(Collectors.toMap(
                Function.identity(), memberships::getRewardProduct));
        List<ItemDTO> foundItems = itemIds.isEmpty() ? List.of() : items.queryItemByIds(itemIds);
        Map<Long, ItemDTO> itemById = foundItems == null ? Map.of() : foundItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ItemDTO::getId, Function.identity(), (left, right) -> left));
        return new TargetIndex(couponById, membershipById, itemById);
    }

    private List<Long> targetIds(List<RewardDefinitionRow> definitions, RewardType type) {
        return definitions.stream().filter(item -> item.getRewardType() == type)
                .map(RewardDefinitionRow::getTargetId).filter(Objects::nonNull).distinct().toList();
    }

    private RewardDefinitionRow reward(PrizeRow prize, Map<Long, RewardDefinitionRow> definitions,
                                       TargetIndex targets) {
        if (prize.getRewardDefinitionId() == null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        RewardDefinitionRow definition = definitions.get(prize.getRewardDefinitionId());
        if (definition == null || !Integer.valueOf(ENABLED).equals(definition.getStatus())
                || definition.getRewardType() == null || definition.getQuantity() == null) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        if (!RewardPrizeTypeMapping.matches(definition.getRewardType(), prize.getPrizeType())) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        validateRewardTarget(definition, targets);
        return definition;
    }

    private void validateRewardTarget(RewardDefinitionRow definition, TargetIndex targets) {
        long quantity = definition.getQuantity();
        if (quantity <= 0) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        switch (definition.getRewardType()) {
            case POINTS -> {
                if (definition.getTargetId() != null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
            }
            case DRAW_CHANCE -> {
                requireDiscrete(definition);
                if (definition.getTargetId() != null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
            }
            case COUPON -> {
                requireDiscrete(definition);
                CouponRewardTemplateDTO template = targets.coupons().get(requiredTarget(definition));
                if (template == null || !Integer.valueOf(ENABLED).equals(template.status())
                        || template.templateCode() == null || template.templateCode().isBlank()) {
                    throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                }
            }
            case MEMBERSHIP -> {
                requireDiscrete(definition);
                MembershipRewardProductDTO product = targets.memberships().get(requiredTarget(definition));
                if (product == null || !Integer.valueOf(ENABLED).equals(product.status())
                        || product.productCode() == null || product.productCode().isBlank()
                        || product.membershipLevel() == null || product.membershipLevel().isBlank()
                        || product.durationDays() == null || product.durationDays() <= 0) {
                    throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                }
            }
            case PRODUCT -> {
                requireDiscrete(definition);
                ItemDTO item = targets.items().get(requiredTarget(definition));
                if (item == null || !Integer.valueOf(ENABLED).equals(item.getStatus())
                        || item.getStock() == null || item.getStock() <= 0 || item.getName() == null
                        || item.getName().isBlank()) {
                    throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                }
            }
        }
    }

    private void requireDiscrete(RewardDefinitionRow definition) {
        if (definition.getQuantity() > MAX_DISCRETE_QUANTITY) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
    }

    private long requiredTarget(RewardDefinitionRow definition) {
        if (definition.getTargetId() == null || definition.getTargetId() <= 0) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        return definition.getTargetId();
    }

    private <T> Map<Long, T> byId(List<T> values, Function<T, Long> idExtractor) {
        return values.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private LotteryDomainException error(LotteryErrorCode code) {
        return new LotteryDomainException(code);
    }

    private record TargetIndex(Map<Long, CouponRewardTemplateDTO> coupons,
                               Map<Long, MembershipRewardProductDTO> memberships,
                               Map<Long, ItemDTO> items) {
    }
}
