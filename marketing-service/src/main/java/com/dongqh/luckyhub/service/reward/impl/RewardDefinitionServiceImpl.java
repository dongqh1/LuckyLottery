package com.dongqh.luckyhub.service.reward.impl;

import com.dongqh.luckyhub.domain.po.coupon.CouponTemplate;
import com.dongqh.luckyhub.mapper.coupon.CouponTemplateMapper;
import com.dongqh.luckyhub.domain.hmall.integration.HmallCatalogClient;
import com.dongqh.luckyhub.domain.hmall.integration.HmallItemView;
import com.dongqh.luckyhub.domain.po.membership.MembershipProduct;
import com.dongqh.luckyhub.mapper.membership.MembershipProductMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.domain.dto.reward.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.domain.po.reward.RewardDefinition;
import com.dongqh.luckyhub.enums.reward.RewardErrorCode;
import com.dongqh.luckyhub.enums.reward.RewardType;
import com.dongqh.luckyhub.mapper.reward.RewardDefinitionMapper;
import com.dongqh.luckyhub.service.reward.RewardDefinitionService;
import com.dongqh.luckyhub.domain.vo.reward.RewardDefinitionView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RewardDefinitionServiceImpl implements RewardDefinitionService {

    private static final int ENABLED = 1;

    private final RewardDefinitionMapper rewardMapper;
    private final HmallCatalogClient hmallCatalog;
    private final ObjectMapper objectMapper;
    private final CouponTemplateMapper couponMapper;
    private final MembershipProductMapper membershipMapper;

    public RewardDefinitionServiceImpl(
            RewardDefinitionMapper rewardMapper,
            HmallCatalogClient hmallCatalog,
            CouponTemplateMapper couponMapper,
            MembershipProductMapper membershipMapper,
            ObjectMapper objectMapper
    ) {
        this.rewardMapper = rewardMapper;
        this.hmallCatalog = hmallCatalog;
        this.objectMapper = objectMapper;
        this.couponMapper = couponMapper;
        this.membershipMapper = membershipMapper;
    }

    @Override
    @Transactional
    public RewardDefinitionView create(CreateRewardDefinitionCommand command) {
        if (command.quantity() == null || command.quantity() <= 0
                || (command.rewardType() != RewardType.POINTS && command.quantity() > 100)) {
            throw new BusinessException(RewardErrorCode.REWARD_TARGET_INVALID);
        }
        validateTarget(command.rewardType(), command.targetId());

        RewardDefinition definition = new RewardDefinition();
        definition.setRewardCode(command.rewardCode().trim());
        definition.setRewardName(command.rewardName().trim());
        definition.setRewardType(command.rewardType());
        definition.setTargetId(command.targetId());
        definition.setQuantity(command.quantity());
        definition.setConfigSnapshot(normalizeJson(command.configSnapshot()));
        definition.setStatus(ENABLED);
        try {
            rewardMapper.insert(definition);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(RewardErrorCode.REWARD_CODE_DUPLICATE);
        }
        return toView(definition);
    }

    @Override
    public RewardDefinitionView get(long id) {
        RewardDefinition definition = rewardMapper.selectById(id);
        if (definition == null) {
            throw new BusinessException(RewardErrorCode.REWARD_NOT_FOUND);
        }
        return toView(definition);
    }

    private void validateTarget(RewardType type, Long targetId) {
        boolean targetRequired = type == RewardType.PRODUCT
                || type == RewardType.COUPON
                || type == RewardType.MEMBERSHIP;
        if (targetRequired != (targetId != null)) {
            throw new BusinessException(RewardErrorCode.REWARD_TARGET_INVALID);
        }
        if (type == RewardType.PRODUCT) {
            HmallItemView item = hmallCatalog.getItem(targetId);
            if (item == null || !item.availableForReward()) {
                throw new BusinessException(RewardErrorCode.REWARD_TARGET_INVALID);
            }
        }
        if (type == RewardType.COUPON) {
            CouponTemplate template = couponMapper.selectById(targetId);
            if (template == null || !Integer.valueOf(ENABLED).equals(template.getStatus())) {
                throw new BusinessException(RewardErrorCode.REWARD_TARGET_INVALID);
            }
        }
        if (type == RewardType.MEMBERSHIP) {
            MembershipProduct product = membershipMapper.selectById(targetId);
            if (product == null || !Integer.valueOf(ENABLED).equals(product.getStatus())) {
                throw new BusinessException(RewardErrorCode.REWARD_TARGET_INVALID);
            }
        }
    }

    private String normalizeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (JacksonException exception) {
            throw new BusinessException(RewardErrorCode.REWARD_CONFIG_INVALID);
        }
    }

    private RewardDefinitionView toView(RewardDefinition definition) {
        return new RewardDefinitionView(
                definition.getId(), definition.getRewardCode(), definition.getRewardName(),
                definition.getRewardType(), definition.getTargetId(), definition.getQuantity(),
                normalizeJson(definition.getConfigSnapshot()), definition.getStatus(),
                definition.getCreatedAt(), definition.getUpdatedAt()
        );
    }
}
