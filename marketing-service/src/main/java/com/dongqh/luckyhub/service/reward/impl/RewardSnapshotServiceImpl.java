package com.dongqh.luckyhub.service.reward.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.domain.po.coupon.CouponTemplate;
import com.dongqh.luckyhub.mapper.coupon.CouponTemplateMapper;
import com.dongqh.luckyhub.domain.po.membership.MembershipProduct;
import com.dongqh.luckyhub.mapper.membership.MembershipProductMapper;
import com.dongqh.luckyhub.domain.hmall.integration.HmallCatalogClient;
import com.dongqh.luckyhub.domain.hmall.integration.HmallItemView;
import com.dongqh.luckyhub.domain.po.prize.MarketingPrize;
import com.dongqh.luckyhub.domain.po.reward.RewardDefinition;
import com.dongqh.luckyhub.enums.reward.RewardErrorCode;
import com.dongqh.luckyhub.enums.reward.RewardType;
import com.dongqh.luckyhub.mapper.reward.RewardDefinitionMapper;
import com.dongqh.luckyhub.domain.model.reward.CouponRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.DrawChanceRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.MembershipRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.PointsRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.ProductRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;
import com.dongqh.luckyhub.service.reward.RewardSnapshotService;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RewardSnapshotServiceImpl implements RewardSnapshotService {
    private static final int ENABLED = 1;
    private static final long MAX_DISCRETE_QUANTITY = 100;

    private final RewardDefinitionMapper rewards;
    private final CouponTemplateMapper coupons;
    private final MembershipProductMapper memberships;
    private final HmallCatalogClient hmallCatalog;
    private final ObjectMapper json;

    public RewardSnapshotServiceImpl(RewardDefinitionMapper rewards, CouponTemplateMapper coupons,
                                     MembershipProductMapper memberships, HmallCatalogClient hmallCatalog,
                                     ObjectMapper json) {
        this.rewards = rewards;
        this.coupons = coupons;
        this.memberships = memberships;
        this.hmallCatalog = hmallCatalog;
        this.json = json;
    }

    @Override
    public Map<Long, RewardSnapshot> resolveForPrizes(List<MarketingPrize> prizes) {
        if (prizes == null) throw new IllegalArgumentException("奖品不能为空");
        List<Long> definitionIds = prizes.stream().map(MarketingPrize::getRewardDefinitionId)
                .filter(Objects::nonNull).distinct().toList();
        if (definitionIds.isEmpty()) return Map.of();

        Map<Long, RewardDefinition> definitions = byId(rewards.selectByIds(definitionIds), RewardDefinition::getId);
        TargetIndex targets = loadTargets(definitions.values().stream().toList());
        Map<Long, RewardSnapshot> result = new HashMap<>();
        for (MarketingPrize prize : prizes) {
            if (prize.getRewardDefinitionId() == null) continue;
            RewardDefinition definition = definitions.get(prize.getRewardDefinitionId());
            if (definition == null || !Integer.valueOf(ENABLED).equals(definition.getStatus())) throw error();
            Object payload;
            try {
                payload = payload(definition, targets);
            } catch (ArithmeticException exception) {
                throw error();
            }
            String payloadJson = write(payload);
            String identity = definition.getId() + "\n" + definition.getRewardCode() + "\n"
                    + definition.getRewardType() + "\n" + Objects.toString(definition.getTargetId(), "")
                    + "\n" + definition.getQuantity() + "\n" + payloadJson;
            result.put(prize.getId(), new RewardSnapshot(definition.getId(), definition.getRewardCode(),
                    definition.getRewardType(), definition.getTargetId(), definition.getQuantity(),
                    payloadJson, digest(identity)));
        }
        return Map.copyOf(result);
    }

    private TargetIndex loadTargets(List<RewardDefinition> definitions) {
        List<Long> couponIds = targetIds(definitions, RewardType.COUPON);
        List<Long> membershipIds = targetIds(definitions, RewardType.MEMBERSHIP);
        List<Long> itemIds = targetIds(definitions, RewardType.PRODUCT);
        Map<Long, CouponTemplate> couponMap = couponIds.isEmpty() ? Map.of()
                : byId(coupons.selectByIds(couponIds), CouponTemplate::getId);
        Map<Long, MembershipProduct> membershipMap = membershipIds.isEmpty() ? Map.of()
                : byId(memberships.selectByIds(membershipIds), MembershipProduct::getId);
        Map<Long, HmallItemView> itemMap = itemIds.stream()
                .map(hmallCatalog::getItem)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(HmallItemView::id, Function.identity()));
        return new TargetIndex(couponMap, membershipMap, itemMap);
    }

    private List<Long> targetIds(List<RewardDefinition> definitions, RewardType type) {
        return definitions.stream().filter(item -> item.getRewardType() == type)
                .map(RewardDefinition::getTargetId).filter(Objects::nonNull).distinct().toList();
    }

    private Object payload(RewardDefinition definition, TargetIndex targets) {
        long quantity = definition.getQuantity();
        if (quantity <= 0) throw error();
        return switch (definition.getRewardType()) {
            case POINTS -> {
                if (definition.getTargetId() != null) throw error();
                yield new PointsRewardPayload(quantity, "抽奖奖励");
            }
            case DRAW_CHANCE -> {
                requireDiscrete(definition);
                yield new DrawChanceRewardPayload(Math.toIntExact(quantity));
            }
            case COUPON -> {
                requireDiscrete(definition);
                CouponTemplate template = targets.coupons().get(requiredTarget(definition));
                if (template == null || !Integer.valueOf(ENABLED).equals(template.getStatus())) throw error();
                yield new CouponRewardPayload(template.getId(), template.getTemplateCode(), Math.toIntExact(quantity));
            }
            case MEMBERSHIP -> {
                requireDiscrete(definition);
                MembershipProduct product = targets.memberships().get(requiredTarget(definition));
                if (product == null || !Integer.valueOf(ENABLED).equals(product.getStatus())) throw error();
                int count = Math.toIntExact(quantity);
                int durationDays = Math.multiplyExact(product.getDurationDays(), count);
                yield new MembershipRewardPayload(product.getId(), product.getProductCode(),
                        product.getMembershipLevel(), durationDays, count);
            }
            case PRODUCT -> {
                requireDiscrete(definition);
                HmallItemView item = targets.items().get(requiredTarget(definition));
                if (item == null || !item.availableForReward() || item.price() == null) throw error();
                yield new ProductRewardPayload(item.id(), item.name(), item.image(), item.price(),
                        Math.toIntExact(quantity));
            }
        };
    }

    private void requireDiscrete(RewardDefinition definition) {
        if (definition.getTargetId() != null && definition.getRewardType() == RewardType.DRAW_CHANCE) throw error();
        if (definition.getQuantity() > MAX_DISCRETE_QUANTITY) throw error();
    }

    private long requiredTarget(RewardDefinition definition) {
        if (definition.getTargetId() == null) throw error();
        return definition.getTargetId();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(RewardErrorCode.REWARD_CONFIG_INVALID);
        }
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> Map<Long, T> byId(List<T> values, Function<T, Long> idExtractor) {
        return values.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private BusinessException error() {
        return new BusinessException(RewardErrorCode.REWARD_TARGET_INVALID);
    }

    private record TargetIndex(Map<Long, CouponTemplate> coupons,
                               Map<Long, MembershipProduct> memberships,
                               Map<Long, HmallItemView> items) {
    }
}
