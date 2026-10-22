package com.dongqh.lotteryservice.drawchance;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.AddressDTO;
import com.dongqh.lotteryservice.domain.dto.drawchance.AdjustDrawChanceCommand;
import com.dongqh.lotteryservice.domain.po.drawchance.DrawChanceAccountRow;
import com.dongqh.lotteryservice.domain.po.drawchance.DrawChanceLedgerRow;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.enums.drawchance.DrawChanceBusinessType;
import com.dongqh.lotteryservice.enums.drawchance.DrawChanceDirection;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.mapper.drawchance.DrawChanceAccountMapper;
import com.dongqh.lotteryservice.mapper.drawchance.DrawChanceLedgerMapper;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrawChanceAdminServiceTests {
    private TestAccounts accounts;
    private TestLedgers ledgers;
    private TestUsers users;
    private DrawChanceAdminService service;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(account(5L, 23L, 5L, 2L));
        ledgers = new TestLedgers();
        users = new TestUsers();
        service = new DrawChanceAdminService(accounts, ledgers, users);
    }

    @Test
    void creditAddsAvailableBalanceAndAuditsOperatorAndReason() {
        var result = service.adjust(23L, 1L,
                new AdjustDrawChanceCommand("adjust-1", DrawChanceDirection.CREDIT, 3L, "联调补发"));

        assertThat(accounts.row.getAvailableBalance()).isEqualTo(8L);
        assertThat(accounts.row.getReservedBalance()).isEqualTo(2L);
        assertThat(ledgers.rows).hasSize(1);
        DrawChanceLedgerRow ledger = ledgers.rows.get(0);
        assertThat(ledger.getBusinessType()).isEqualTo(DrawChanceBusinessType.MANUAL_ADJUSTMENT);
        assertThat(ledger.getDirection()).isEqualTo(DrawChanceDirection.CREDIT);
        assertThat(ledger.getAmount()).isEqualTo(3L);
        assertThat(ledger.getAvailableAfter()).isEqualTo(8L);
        assertThat(ledger.getReservedAfter()).isEqualTo(2L);
        assertThat(ledger.getOperatorUserId()).isEqualTo(1L);
        assertThat(ledger.getReason()).isEqualTo("联调补发");
        assertThat(result.account().availableBalance()).isEqualTo(8L);
        assertThat(result.account().reservedBalance()).isEqualTo(2L);
    }

    @Test
    void debitOnlyConsumesAvailableBalanceAndKeepsReservedBalance() {
        var result = service.adjust(23L, 1L,
                new AdjustDrawChanceCommand("adjust-1", DrawChanceDirection.DEBIT, 3L, "重复发放修正"));

        assertThat(accounts.row.getAvailableBalance()).isEqualTo(2L);
        assertThat(accounts.row.getReservedBalance()).isEqualTo(2L);
        assertThat(result.account().availableBalance()).isEqualTo(2L);
        assertThat(result.account().reservedBalance()).isEqualTo(2L);
        assertThat(ledgers.rows.get(0).getDirection()).isEqualTo(DrawChanceDirection.DEBIT);
        assertThat(ledgers.rows.get(0).getReservedAfter()).isEqualTo(2L);
    }

    @Test
    void debitRejectsInsufficientAvailableBalanceWithoutWritingAnything() {
        accounts.row = account(5L, 23L, 2L, 9L);

        assertThatThrownBy(() -> service.adjust(23L, 1L,
                new AdjustDrawChanceCommand("adjust-1", DrawChanceDirection.DEBIT, 3L, "扣减测试")))
                .isInstanceOfSatisfying(LotteryDomainException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_INSUFFICIENT_AVAILABLE));

        assertThat(accounts.updateCount).isZero();
        assertThat(ledgers.rows).isEmpty();
    }

    @Test
    void repeatingSameAdjustmentIdDoesNotChangeBalanceTwice() {
        DrawChanceLedgerRow existing = ledger(23L, 1L, "adjust-1", DrawChanceDirection.CREDIT, 3L, "联调补发");
        existing.setAvailableAfter(8L);
        existing.setReservedAfter(2L);
        ledgers.save(existing);

        var result = service.adjust(23L, 1L,
                new AdjustDrawChanceCommand("adjust-1", DrawChanceDirection.CREDIT, 3L, "联调补发"));

        assertThat(accounts.updateCount).isZero();
        assertThat(accounts.row.getAvailableBalance()).isEqualTo(5L);
        assertThat(ledgers.rows).hasSize(1);
        assertThat(result.ledger().businessId()).isEqualTo("adjust-1");
    }

    @Test
    void reusingAdjustmentIdWithDifferentTermsIsRejected() {
        ledgers.save(ledger(23L, 1L, "adjust-1", DrawChanceDirection.CREDIT, 2L, "联调补发"));

        assertThatThrownBy(() -> service.adjust(23L, 1L,
                new AdjustDrawChanceCommand("adjust-1", DrawChanceDirection.CREDIT, 3L, "联调补发")))
                .isInstanceOfSatisfying(LotteryDomainException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT));

        assertThat(accounts.updateCount).isZero();
        assertThat(accounts.row.getAvailableBalance()).isEqualTo(5L);
    }

    @Test
    void ledgerPageIsScopedToUserAndUsesDescendingCreationOrder() {
        DrawChanceLedgerRow older = ledger(23L, 1L, "old", DrawChanceDirection.CREDIT, 1L, "旧记录");
        older.setCreatedAt(java.time.LocalDateTime.parse("2026-10-20T10:00:00"));
        DrawChanceLedgerRow newer = ledger(23L, 1L, "new", DrawChanceDirection.CREDIT, 2L, "新记录");
        newer.setCreatedAt(java.time.LocalDateTime.parse("2026-10-21T10:00:00"));
        ledgers.save(older);
        ledgers.save(newer);
        ledgers.save(ledger(24L, 1L, "other", DrawChanceDirection.CREDIT, 5L, "其他用户"));

        var page = service.pageLedger(23L, 1, 1);

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).businessId()).isEqualTo("new");
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.pages()).isEqualTo(2L);
    }

    private DrawChanceAccountRow account(long id, long userId, long available, long reserved) {
        DrawChanceAccountRow row = new DrawChanceAccountRow();
        row.setId(id);
        row.setUserId(userId);
        row.setAvailableBalance(available);
        row.setReservedBalance(reserved);
        return row;
    }

    private DrawChanceLedgerRow ledger(long userId, long operatorId, String businessId,
                                       DrawChanceDirection direction, long amount, String reason) {
        DrawChanceLedgerRow row = new DrawChanceLedgerRow();
        row.setUserId(userId);
        row.setOperatorUserId(operatorId);
        row.setBusinessType(DrawChanceBusinessType.MANUAL_ADJUSTMENT);
        row.setBusinessId(businessId);
        row.setDirection(direction);
        row.setAmount(amount);
        row.setAvailableAfter(0L);
        row.setReservedAfter(0L);
        row.setReason(reason);
        return row;
    }

    private static class TestAccounts implements DrawChanceAccountMapper {
        private DrawChanceAccountRow row;
        private int updateCount;

        TestAccounts(DrawChanceAccountRow row) { this.row = row; }
        @Override public int insertIfAbsent(long userId) { return 1; }
        @Override public DrawChanceAccountRow lockByUserId(long userId) { return row; }
        @Override public DrawChanceAccountRow selectByUserId(long userId) { return row; }
        @Override public int updateBalances(long id, long available, long reserved) {
            updateCount++;
            row.setAvailableBalance(available);
            row.setReservedBalance(reserved);
            return 1;
        }
    }

    private static class TestLedgers implements DrawChanceLedgerMapper {
        private final List<DrawChanceLedgerRow> rows = new ArrayList<>();
        private final Map<String, DrawChanceLedgerRow> byBusiness = new HashMap<>();

        @Override public int insert(DrawChanceLedgerRow ledger) { save(ledger); return 1; }
        @Override public int insertAdjustment(DrawChanceLedgerRow ledger) { save(ledger); return 1; }
        @Override public DrawChanceLedgerRow selectBusiness(DrawChanceBusinessType type, String businessId) {
            return byBusiness.get(key(type, businessId));
        }
        @Override public List<DrawChanceLedgerRow> selectUserPage(long userId, long limit, long offset) {
            return rows.stream().filter(row -> row.getUserId() == userId)
                    .sorted(Comparator.comparing(DrawChanceLedgerRow::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .skip(offset).limit(limit).toList();
        }
        @Override public long countUser(long userId) {
            return rows.stream().filter(row -> row.getUserId() == userId).count();
        }
        void save(DrawChanceLedgerRow row) {
            if (byBusiness.putIfAbsent(key(row.getBusinessType(), row.getBusinessId()), row) != null) {
                throw new DuplicateKeyException("duplicate ledger business key");
            }
            rows.add(row);
        }
        private String key(DrawChanceBusinessType type, String businessId) { return type + ":" + businessId; }
    }

    private static class TestUsers implements UserClient {
        @Override public AddressDTO findAddressById(Long id) { return null; }
        @Override public Boolean isEnabled(Long userId) { return true; }
        @Override public void deductMoney(String password, Integer amount) { }
    }
}
