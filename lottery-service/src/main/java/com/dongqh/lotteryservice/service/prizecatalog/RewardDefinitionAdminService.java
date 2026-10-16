package com.dongqh.lotteryservice.service.prizecatalog;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardDefinitionErrorCode;
import com.dongqh.lotteryservice.exception.prizecatalog.RewardDefinitionException;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreateRewardDefinitionCommand;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.RewardDefinitionView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class RewardDefinitionAdminService {
    private static final int ENABLED = 1;
    private final RewardDefinitionMapper rewards;
    private final ItemClient items;
    private final CouponClient coupons;
    private final MembershipClient memberships;
    private final ObjectMapper json;

    public RewardDefinitionAdminService(RewardDefinitionMapper rewards, ItemClient items, CouponClient coupons,
                                       MembershipClient memberships, ObjectMapper json) {
        this.rewards = rewards;
        this.items = items;
        this.coupons = coupons;
        this.memberships = memberships;
        this.json = json;
    }

    @Transactional
    public RewardDefinitionView create(CreateRewardDefinitionCommand command) {
        if (command == null || command.rewardType() == null || command.quantity() == null
                || command.quantity() <= 0
                || (command.rewardType() != RewardType.POINTS && command.quantity() > 100)) {
            throw error(RewardDefinitionErrorCode.REWARD_TARGET_INVALID);
        }
        validateTarget(command.rewardType(), command.targetId());
        RewardDefinitionRow row = new RewardDefinitionRow();
        row.setRewardCode(command.rewardCode().trim());
        row.setRewardName(command.rewardName().trim());
        row.setRewardType(command.rewardType());
        row.setTargetId(command.targetId());
        row.setQuantity(command.quantity());
        row.setConfigSnapshot(normalizeJson(command.configSnapshot()));
        row.setStatus(ENABLED);
        try {
            rewards.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw error(RewardDefinitionErrorCode.REWARD_CODE_DUPLICATE);
        }
        return view(rewards.selectById(row.getId()));
    }

    public RewardDefinitionView get(long id) {
        RewardDefinitionRow row = rewards.selectById(id);
        if (row == null) throw error(RewardDefinitionErrorCode.REWARD_NOT_FOUND);
        return view(row);
    }

    private void validateTarget(RewardType type, Long targetId) {
        boolean required = type == RewardType.PRODUCT || type == RewardType.COUPON || type == RewardType.MEMBERSHIP;
        if (required != (targetId != null) || targetId != null && targetId <= 0) {
            throw error(RewardDefinitionErrorCode.REWARD_TARGET_INVALID);
        }
        if (type == RewardType.PRODUCT) {
            List<ItemDTO> found = items.queryItemByIds(List.of(targetId));
            ItemDTO item = found == null ? null : found.stream()
                    .filter(value -> value != null && targetId.equals(value.getId())).findFirst().orElse(null);
            if (item == null || !Integer.valueOf(ENABLED).equals(item.getStatus())
                    || item.getStock() == null || item.getStock() <= 0) {
                throw error(RewardDefinitionErrorCode.REWARD_TARGET_INVALID);
            }
        } else if (type == RewardType.COUPON) {
            CouponRewardTemplateDTO template = coupons.getRewardTemplate(targetId);
            if (template == null || !Integer.valueOf(ENABLED).equals(template.status())) {
                throw error(RewardDefinitionErrorCode.REWARD_TARGET_INVALID);
            }
        } else if (type == RewardType.MEMBERSHIP) {
            MembershipRewardProductDTO product = memberships.getRewardProduct(targetId);
            if (product == null || !Integer.valueOf(ENABLED).equals(product.status())) {
                throw error(RewardDefinitionErrorCode.REWARD_TARGET_INVALID);
            }
        }
    }

    private String normalizeJson(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return json.writeValueAsString(json.readTree(value));
        } catch (JacksonException exception) {
            throw error(RewardDefinitionErrorCode.REWARD_CONFIG_INVALID);
        }
    }

    private RewardDefinitionView view(RewardDefinitionRow row) {
        return new RewardDefinitionView(row.getId(), row.getRewardCode(), row.getRewardName(), row.getRewardType(),
                row.getTargetId(), row.getQuantity(), normalizeJson(row.getConfigSnapshot()), row.getStatus(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private RewardDefinitionException error(RewardDefinitionErrorCode code) {
        return new RewardDefinitionException(code);
    }
}
