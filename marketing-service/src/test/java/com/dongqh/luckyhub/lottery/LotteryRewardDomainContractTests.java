package com.dongqh.luckyhub.lottery;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.domain.po.drawchance.DrawChanceAccount;
import com.dongqh.luckyhub.domain.po.drawchance.DrawChanceLedger;
import com.dongqh.luckyhub.domain.po.drawchance.DrawChanceReservation;
import com.dongqh.luckyhub.enums.drawchance.DrawChanceBusinessType;
import com.dongqh.luckyhub.enums.drawchance.DrawChanceDirection;
import com.dongqh.luckyhub.enums.drawchance.DrawChanceReservationStatus;
import com.dongqh.luckyhub.mapper.drawchance.DrawChanceAccountMapper;
import com.dongqh.luckyhub.mapper.drawchance.DrawChanceLedgerMapper;
import com.dongqh.luckyhub.mapper.drawchance.DrawChanceReservationMapper;
import com.dongqh.luckyhub.domain.po.lottery.LotteryRewardQuarantine;
import com.dongqh.luckyhub.enums.lottery.RewardQuarantineStatus;
import com.dongqh.luckyhub.mapper.lottery.LotteryRewardQuarantineMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryRewardDomainContractTests {

    @Test
    void exposesStableDrawChanceAndQuarantineEnums() {
        assertThat(DrawChanceBusinessType.values()).containsExactly(
                DrawChanceBusinessType.LOTTERY_REWARD,
                DrawChanceBusinessType.DRAW_CONSUME,
                DrawChanceBusinessType.DRAW_RELEASE,
                DrawChanceBusinessType.MANUAL_ADJUSTMENT);
        assertThat(DrawChanceDirection.values()).containsExactly(
                DrawChanceDirection.CREDIT, DrawChanceDirection.DEBIT);
        assertThat(DrawChanceReservationStatus.values()).containsExactly(
                DrawChanceReservationStatus.RESERVED,
                DrawChanceReservationStatus.CONFIRMED,
                DrawChanceReservationStatus.RELEASED);
        assertThat(RewardQuarantineStatus.values()).containsExactly(
                RewardQuarantineStatus.OPEN,
                RewardQuarantineStatus.RESOLVED,
                RewardQuarantineStatus.IGNORED);
    }

    @Test
    void mapsStageFiveEntitiesAndMappers() {
        assertTable(DrawChanceAccount.class, "draw_chance_account");
        assertTable(DrawChanceLedger.class, "draw_chance_ledger");
        assertTable(DrawChanceReservation.class, "draw_chance_reservation");
        assertTable(LotteryRewardQuarantine.class, "lottery_reward_quarantine");
        assertThat(DrawChanceAccountMapper.class).isInterface();
        assertThat(DrawChanceLedgerMapper.class).isInterface();
        assertThat(DrawChanceReservationMapper.class).isInterface();
        assertThat(LotteryRewardQuarantineMapper.class).isInterface();
    }

    private void assertTable(Class<?> entityType, String expected) {
        assertThat(entityType.getAnnotation(TableName.class)).isNotNull();
        assertThat(entityType.getAnnotation(TableName.class).value()).isEqualTo(expected);
    }
}
