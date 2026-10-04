package com.dongqh.lotteryservice.prizecatalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeCatalogException;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeErrorCode;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeLevel;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardPrizeTypeMapping;
import com.dongqh.lotteryservice.prizecatalog.dto.CreatePrizeCommand;
import com.dongqh.lotteryservice.prizecatalog.dto.PrizeQuery;
import com.dongqh.lotteryservice.prizecatalog.dto.UpdatePrizeCommand;
import com.dongqh.lotteryservice.prizecatalog.entity.PrizeRow;
import com.dongqh.lotteryservice.prizecatalog.entity.RewardDefinitionRow;
import com.dongqh.lotteryservice.prizecatalog.mapper.PrizeCatalogMapper;
import com.dongqh.lotteryservice.prizecatalog.mapper.RewardDefinitionMapper;
import com.dongqh.lotteryservice.prizecatalog.vo.PageResponse;
import com.dongqh.lotteryservice.prizecatalog.vo.PrizeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PrizeCatalogService {
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final PrizeCatalogMapper mapper;
    private final RewardDefinitionMapper rewards;

    public PrizeCatalogService(PrizeCatalogMapper mapper, RewardDefinitionMapper rewards) {
        this.mapper = mapper;
        this.rewards = rewards;
    }

    @Transactional
    public PrizeView create(CreatePrizeCommand command) {
        PrizeRow prize = new PrizeRow();
        apply(prize, command.prizeName(), command.prizeType(), command.prizeLevel(),
                command.imageUrl(), command.description(), command.stackable());
        prize.setRewardDefinitionId(requireCompatibleReward(command.rewardDefinitionId(), command.prizeType()).getId());
        prize.setStatus(ENABLED);
        mapper.insert(prize);
        return toView(prize);
    }

    public PrizeView getById(long id) {
        return toView(requirePrize(id));
    }

    public PageResponse<PrizeView> page(PrizeQuery query) {
        String name = normalize(query.getName());
        LambdaQueryWrapper<PrizeRow> wrapper = new LambdaQueryWrapper<PrizeRow>()
                .like(name != null, PrizeRow::getPrizeName, name)
                .eq(query.getType() != null, PrizeRow::getPrizeType, query.getType())
                .eq(query.getStatus() != null, PrizeRow::getStatus, query.getStatus())
                .orderByDesc(PrizeRow::getCreatedAt)
                .orderByDesc(PrizeRow::getId);
        Page<PrizeRow> result = mapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PrizeView> records = result.getRecords().stream().map(this::toView).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Transactional
    public PrizeView update(long id, UpdatePrizeCommand command) {
        PrizeRow prize = requirePrize(id);
        apply(prize, command.prizeName(), command.prizeType(), command.prizeLevel(),
                command.imageUrl(), command.description(), command.stackable());
        if (command.rewardDefinitionId() != null) {
            if (prize.getRewardDefinitionId() != null
                    && !prize.getRewardDefinitionId().equals(command.rewardDefinitionId())) {
                throw new PrizeCatalogException(PrizeErrorCode.REWARD_BINDING_INVALID);
            }
            prize.setRewardDefinitionId(requireCompatibleReward(command.rewardDefinitionId(), command.prizeType()).getId());
        } else if (prize.getRewardDefinitionId() != null) {
            requireCompatibleReward(prize.getRewardDefinitionId(), command.prizeType());
        }
        mapper.updateById(prize);
        return toView(prize);
    }

    @Transactional
    public void disable(long id) {
        PrizeRow prize = requirePrize(id);
        if (Integer.valueOf(DISABLED).equals(prize.getStatus())) return;
        prize.setStatus(DISABLED);
        mapper.updateById(prize);
    }

    private PrizeRow requirePrize(long id) {
        PrizeRow prize = mapper.selectById(id);
        if (prize == null) throw new PrizeCatalogException(PrizeErrorCode.PRIZE_NOT_FOUND);
        return prize;
    }

    private void apply(PrizeRow prize, String prizeName, PrizeType prizeType, PrizeLevel prizeLevel,
                       String imageUrl, String description, Boolean stackable) {
        prize.setPrizeName(prizeName.trim());
        prize.setPrizeType(prizeType);
        prize.setPrizeLevel(prizeLevel);
        prize.setImageUrl(normalize(imageUrl));
        prize.setDescription(normalize(description));
        prize.setStackable(stackable);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private RewardDefinitionRow requireCompatibleReward(Long rewardId, PrizeType prizeType) {
        if (rewardId == null) throw new PrizeCatalogException(PrizeErrorCode.REWARD_BINDING_INVALID);
        RewardDefinitionRow reward = rewards.selectById(rewardId);
        if (reward == null || !Integer.valueOf(ENABLED).equals(reward.getStatus())
                || !RewardPrizeTypeMapping.matches(reward.getRewardType(), prizeType)) {
            throw new PrizeCatalogException(PrizeErrorCode.REWARD_BINDING_INVALID);
        }
        return reward;
    }

    private PrizeView toView(PrizeRow prize) {
        RewardDefinitionRow reward = prize.getRewardDefinitionId() == null
                ? null : rewards.selectById(prize.getRewardDefinitionId());
        return new PrizeView(prize.getId(), prize.getPrizeName(), prize.getPrizeType(), prize.getPrizeLevel(),
                prize.getImageUrl(), prize.getDescription(), prize.getStackable(), prize.getStatus(),
                prize.getCreatedAt(), prize.getUpdatedAt(), prize.getRewardDefinitionId(),
                reward == null ? null : reward.getRewardType());
    }
}
