package com.dongqh.lotteryservice.prizecatalog;

import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreatePrizeCommand;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreateRewardDefinitionCommand;
import com.dongqh.lotteryservice.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.RewardDefinitionView;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeLevel;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import com.dongqh.lotteryservice.service.prizecatalog.PrizeCatalogService;
import com.dongqh.lotteryservice.service.prizecatalog.RewardDefinitionAdminService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrizeCatalogSingleCommandFlowTests {
    @Test
    void onePrizeCommandCreatesInternalRewardAndReturnsPrizeSummary() {
        Map<Long, RewardDefinitionRow> rewardRows = new HashMap<>();
        Map<Long, PrizeRow> prizeRows = new HashMap<>();
        RewardDefinitionMapper rewardMapper = proxy(RewardDefinitionMapper.class, (proxy, method, args) -> {
            if (method.getName().equals("selectById")) return rewardRows.get(args[0]);
            return null;
        });
        PrizeCatalogMapper prizeMapper = proxy(PrizeCatalogMapper.class, (proxy, method, args) -> {
            if (method.getName().equals("insert")) {
                PrizeRow row = (PrizeRow) args[0];
                row.setId(91L);
                prizeRows.put(row.getId(), row);
                return 1;
            }
            if (method.getName().equals("selectById")) return prizeRows.get(args[0]);
            return null;
        });
        RewardCreator rewardCreator = new RewardCreator(rewardRows);
        PrizeCatalogService service = new PrizeCatalogService(prizeMapper, rewardMapper, rewardCreator);

        var prize = service.create(new CreatePrizeCommand("会员季卡", RewardType.MEMBERSHIP,
                PrizeLevel.FIRST, null, "三十天会员", true, 15L, 1L));

        assertThat(rewardCreator.command).isNotNull();
        assertThat(rewardCreator.command.rewardCode()).startsWith("PRIZE-");
        assertThat(rewardCreator.command.rewardName()).isEqualTo("会员季卡");
        assertThat(rewardCreator.command.rewardType()).isEqualTo(RewardType.MEMBERSHIP);
        assertThat(rewardCreator.command.targetId()).isEqualTo(15L);
        assertThat(rewardCreator.command.quantity()).isEqualTo(1L);
        assertThat(prizeRows.get(91L).getPrizeType()).isEqualTo(PrizeType.MEMBERSHIP);
        assertThat(prizeRows.get(91L).getRewardDefinitionId()).isEqualTo(17L);
        assertThat(prize.rewardType()).isEqualTo(RewardType.MEMBERSHIP);
        assertThat(prize.targetId()).isEqualTo(15L);
        assertThat(prize.quantity()).isEqualTo(1L);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static class RewardCreator extends RewardDefinitionAdminService {
        private final Map<Long, RewardDefinitionRow> rows;
        private CreateRewardDefinitionCommand command;

        RewardCreator(Map<Long, RewardDefinitionRow> rows) {
            super(null, null, null, null);
            this.rows = rows;
        }

        @Override
        public RewardDefinitionView create(CreateRewardDefinitionCommand command) {
            this.command = command;
            RewardDefinitionRow row = new RewardDefinitionRow();
            row.setId(17L);
            row.setRewardCode(command.rewardCode());
            row.setRewardName(command.rewardName());
            row.setRewardType(command.rewardType());
            row.setTargetId(command.targetId());
            row.setQuantity(command.quantity());
            row.setStatus(1);
            rows.put(row.getId(), row);
            return new RewardDefinitionView(row.getId(), row.getRewardCode(), row.getRewardName(), row.getRewardType(),
                    row.getTargetId(), row.getQuantity(), row.getStatus(), null, null);
        }
    }
}
