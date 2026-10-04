package com.dongqh.lotteryservice.draweligibility;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.lotteryservice.activityprize.domain.ActivityStatus;
import com.dongqh.lotteryservice.activityprize.entity.ActivityPrizeRow;
import com.dongqh.lotteryservice.activityprize.entity.ActivityRow;
import com.dongqh.lotteryservice.activityprize.mapper.ActivityMapper;
import com.dongqh.lotteryservice.activityprize.mapper.ActivityPrizeMapper;
import com.dongqh.lotteryservice.drawtransaction.model.CouponRewardPayload;
import com.dongqh.lotteryservice.drawtransaction.model.DrawChanceRewardPayload;
import com.dongqh.lotteryservice.drawtransaction.model.DrawPrizeSnapshot;
import com.dongqh.lotteryservice.drawtransaction.model.MembershipRewardPayload;
import com.dongqh.lotteryservice.drawtransaction.model.PointsRewardPayload;
import com.dongqh.lotteryservice.drawtransaction.model.ProductRewardPayload;
import com.dongqh.lotteryservice.drawtransaction.model.RewardSnapshot;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;
import com.dongqh.lotteryservice.prizecatalog.entity.PrizeRow;
import com.dongqh.lotteryservice.prizecatalog.entity.RewardDefinitionRow;
import com.dongqh.lotteryservice.prizecatalog.mapper.PrizeCatalogMapper;
import com.dongqh.lotteryservice.prizecatalog.mapper.RewardDefinitionMapper;
import com.dongqh.lotteryservice.config.LotteryProperties;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
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
    private final ObjectMapper json;
    private final Clock clock;

    public DrawEligibilityService(ActivityMapper activities, ActivityPrizeMapper activityPrizes,
                                  PrizeCatalogMapper prizes, RewardDefinitionMapper rewardDefinitions,
                                  ItemClient items, CouponClient coupons, MembershipClient memberships,
                                  ObjectMapper json, LotteryProperties properties) {
        this.activities = activities;
        this.activityPrizes = activityPrizes;
        this.prizes = prizes;
        this.rewardDefinitions = rewardDefinitions;
        this.items = items;
        this.coupons = coupons;
        this.memberships = memberships;
        this.json = json;
        this.clock = Clock.system(properties.zoneId());
    }

    public EligibilitySnapshot load(long activityId) {
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

        List<DrawPrizeSnapshot> snapshots = relations.stream().map(relation -> {
            PrizeRow prize = prizeById.get(relation.getPrizeId());
            if (prize == null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
            RewardSnapshot reward = reward(prize, definitions, targets);
            return new DrawPrizeSnapshot(relation.getId(), prize.getId(), prize.getPrizeName(),
                    prize.getPrizeType(), prize.getImageUrl(), relation.getWeight(),
                    relation.getRemainingStock(), Integer.valueOf(ENABLED).equals(prize.getStatus()), reward);
        }).toList();

        int dailyLimit = activity.getDailyLimit() == null ? 0 : activity.getDailyLimit();
        int noWinWeight = activity.getNoWinWeight() == null ? 0 : activity.getNoWinWeight();
        if (dailyLimit < 0 || noWinWeight < 0 || (snapshots.isEmpty() && noWinWeight == 0)) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        return new EligibilitySnapshot(activityId, dailyLimit, noWinWeight, snapshots, now);
    }

    private TargetIndex loadTargets(List<RewardDefinitionRow> definitions) {
        List<Long> couponIds = targetIds(definitions, RewardType.COUPON);
        List<Long> membershipIds = targetIds(definitions, RewardType.MEMBERSHIP);
        List<Long> itemIds = targetIds(definitions, RewardType.PRODUCT);
        Map<Long, CouponRewardTemplateDTO> couponById = couponIds.stream().collect(Collectors.toMap(
                Function.identity(), coupons::getRewardTemplate));
        Map<Long, MembershipRewardProductDTO> membershipById = membershipIds.stream().collect(Collectors.toMap(
                Function.identity(), memberships::getRewardProduct));
        Map<Long, ItemDTO> itemById = itemIds.isEmpty() ? Map.of()
                : items.queryItemByIds(itemIds).stream().filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ItemDTO::getId, Function.identity(), (left, right) -> left));
        return new TargetIndex(couponById, membershipById, itemById);
    }

    private List<Long> targetIds(List<RewardDefinitionRow> definitions, RewardType type) {
        return definitions.stream().filter(item -> item.getRewardType() == type)
                .map(RewardDefinitionRow::getTargetId).filter(Objects::nonNull).distinct().toList();
    }

    private RewardSnapshot reward(PrizeRow prize, Map<Long, RewardDefinitionRow> definitions, TargetIndex targets) {
        if (prize.getRewardDefinitionId() == null) return null;
        RewardDefinitionRow definition = definitions.get(prize.getRewardDefinitionId());
        if (definition == null || !Integer.valueOf(ENABLED).equals(definition.getStatus())
                || definition.getRewardCode() == null || definition.getRewardCode().isBlank()
                || definition.getRewardType() == null || definition.getQuantity() == null) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        Object payload;
        try {
            payload = payload(definition, targets);
        } catch (ArithmeticException exception) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        String payloadJson;
        try {
            payloadJson = json.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        String identity = definition.getId() + "\n" + definition.getRewardCode() + "\n"
                + definition.getRewardType() + "\n" + Objects.toString(definition.getTargetId(), "")
                + "\n" + definition.getQuantity() + "\n" + payloadJson;
        return new RewardSnapshot(definition.getId(), definition.getRewardCode(), definition.getRewardType(),
                definition.getTargetId(), definition.getQuantity(), payloadJson, digest(identity));
    }

    private Object payload(RewardDefinitionRow definition, TargetIndex targets) {
        long quantity = definition.getQuantity();
        if (quantity <= 0) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        return switch (definition.getRewardType()) {
            case POINTS -> {
                if (definition.getTargetId() != null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                yield new PointsRewardPayload(quantity, "抽奖奖励");
            }
            case DRAW_CHANCE -> {
                requireDiscrete(definition);
                if (definition.getTargetId() != null) throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                yield new DrawChanceRewardPayload(Math.toIntExact(quantity));
            }
            case COUPON -> {
                requireDiscrete(definition);
                CouponRewardTemplateDTO template = targets.coupons().get(requiredTarget(definition));
                if (template == null || !Integer.valueOf(ENABLED).equals(template.status())) {
                    throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                }
                yield new CouponRewardPayload(template.id(), template.templateCode(), Math.toIntExact(quantity));
            }
            case MEMBERSHIP -> {
                requireDiscrete(definition);
                MembershipRewardProductDTO product = targets.memberships().get(requiredTarget(definition));
                if (product == null || !Integer.valueOf(ENABLED).equals(product.status())
                        || product.durationDays() == null || product.durationDays() <= 0) {
                    throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                }
                int count = Math.toIntExact(quantity);
                yield new MembershipRewardPayload(product.id(), product.productCode(), product.membershipLevel(),
                        Math.multiplyExact(product.durationDays(), count), count);
            }
            case PRODUCT -> {
                requireDiscrete(definition);
                ItemDTO item = targets.items().get(requiredTarget(definition));
                if (item == null || !Integer.valueOf(ENABLED).equals(item.getStatus())
                        || item.getStock() == null || item.getStock() <= 0 || item.getPrice() == null) {
                    throw error(LotteryErrorCode.DRAW_WEIGHT_INVALID);
                }
                yield new ProductRewardPayload(item.getId(), item.getName(), item.getImage(), item.getPrice(),
                        Math.toIntExact(quantity));
            }
        };
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

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
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
