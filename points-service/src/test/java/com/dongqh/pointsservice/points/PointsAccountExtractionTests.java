package com.dongqh.pointsservice.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.entity.PointsAccount;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.exception.PointsDomainException;
import com.dongqh.luckyhub.points.mapper.PointsAccountMapper;
import com.dongqh.luckyhub.points.mapper.PointsLedgerMapper;
import com.dongqh.luckyhub.points.service.impl.PointsAccountServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PointsAccountExtractionTests {

    private final PointsAccountMapper accounts = mock(PointsAccountMapper.class);
    private final PointsLedgerMapper ledger = mock(PointsLedgerMapper.class);

    @Test
    void rejectsCreditWhenUserEligibilityIsNotConfirmedBeforeWriting() {
        PointsAccountServiceImpl service = new PointsAccountServiceImpl(userId -> false, accounts, ledger);

        assertThatThrownBy(() -> service.credit(new PointsMutationCommand(
                42L, PointsBusinessType.LOTTERY_REWARD, "draw-1", 25L, null)))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(PointsErrorCode.POINTS_USER_UNAVAILABLE));
        verifyNoInteractions(accounts, ledger);
    }

    @Test
    void returnsZeroForEligibleUserWithoutCreatingAnAccount() {
        when(accounts.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        PointsAccountServiceImpl service = new PointsAccountServiceImpl(userId -> userId == 42L, accounts, ledger);

        var account = service.get(42L);

        assertThat(account.userId()).isEqualTo(42L);
        assertThat(account.balance()).isZero();
        assertThat(account.updatedAt()).isNull();
    }
}
