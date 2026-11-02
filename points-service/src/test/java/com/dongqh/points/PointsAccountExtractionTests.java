package com.dongqh.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.points.domain.dto.points.PointsMutationCommand;
import com.dongqh.points.domain.po.points.PointsAccount;
import com.dongqh.points.enums.points.PointsBusinessType;
import com.dongqh.points.enums.points.PointsErrorCode;
import com.dongqh.points.domain.exception.PointsDomainException;
import com.dongqh.points.integration.PointsIntegrationService;
import com.dongqh.points.mapper.points.PointsAccountMapper;
import com.dongqh.points.mapper.points.PointsLedgerMapper;
import com.dongqh.points.service.points.impl.PointsAccountServiceImpl;
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
    private final PointsIntegrationService integration = mock(PointsIntegrationService.class);

    @Test
    void rejectsCreditWhenUserEligibilityIsNotConfirmedBeforeWriting() {
        when(integration.isEnabledUser(42L)).thenReturn(false);
        PointsAccountServiceImpl service = new PointsAccountServiceImpl(integration, accounts, ledger);

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
        when(integration.isEnabledUser(42L)).thenReturn(true);
        PointsAccountServiceImpl service = new PointsAccountServiceImpl(integration, accounts, ledger);

        var account = service.get(42L);

        assertThat(account.userId()).isEqualTo(42L);
        assertThat(account.balance()).isZero();
        assertThat(account.updatedAt()).isNull();
    }
}
