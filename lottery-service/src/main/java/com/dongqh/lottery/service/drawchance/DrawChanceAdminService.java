package com.dongqh.lottery.service.drawchance;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.lottery.domain.dto.drawchance.AdjustDrawChanceCommand;
import com.dongqh.lottery.domain.po.drawchance.DrawChanceAccountRow;
import com.dongqh.lottery.domain.po.drawchance.DrawChanceLedgerRow;
import com.dongqh.lottery.domain.vo.drawchance.DrawChanceAccountView;
import com.dongqh.lottery.domain.vo.drawchance.DrawChanceAdjustmentView;
import com.dongqh.lottery.domain.vo.drawchance.DrawChanceLedgerView;
import com.dongqh.lottery.domain.vo.prizecatalog.PageResponse;
import com.dongqh.lottery.enums.LotteryErrorCode;
import com.dongqh.lottery.enums.drawchance.DrawChanceBusinessType;
import com.dongqh.lottery.enums.drawchance.DrawChanceDirection;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.mapper.drawchance.DrawChanceAccountMapper;
import com.dongqh.lottery.mapper.drawchance.DrawChanceLedgerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class DrawChanceAdminService {
    private static final long MAX_PAGE_SIZE = 100;

    private final DrawChanceAccountMapper accounts;
    private final DrawChanceLedgerMapper ledgers;
    private final UserClient users;

    public DrawChanceAdminService(DrawChanceAccountMapper accounts, DrawChanceLedgerMapper ledgers, UserClient users) {
        this.accounts = accounts;
        this.ledgers = ledgers;
        this.users = users;
    }

    public DrawChanceAccountView getAccount(long userId) {
        requireEnabledUser(userId);
        DrawChanceAccountRow account = accounts.selectByUserId(userId);
        return account == null ? new DrawChanceAccountView(userId, 0, 0) : accountView(account);
    }

    public PageResponse<DrawChanceLedgerView> pageLedger(long userId, long page, long size) {
        requireEnabledUser(userId);
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        }
        long offset;
        try {
            offset = Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException exception) {
            throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        }
        List<DrawChanceLedgerView> records = ledgers.selectUserPage(userId, size, offset).stream()
                .map(this::ledgerView).toList();
        long total = ledgers.countUser(userId);
        long pages = (total + size - 1) / size;
        return new PageResponse<>(records, total, page, size, pages);
    }

    @Transactional
    public DrawChanceAdjustmentView adjust(long userId, long operatorUserId, AdjustDrawChanceCommand command) {
        validateAdjustment(userId, operatorUserId, command);
        requireEnabledUser(userId);

        accounts.insertIfAbsent(userId);
        DrawChanceAccountRow account = accounts.lockByUserId(userId);
        if (account == null) throw error(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID);

        String reason = command.reason().trim();
        DrawChanceLedgerRow existing = ledgers.selectBusiness(
                DrawChanceBusinessType.MANUAL_ADJUSTMENT, command.adjustmentId().trim());
        if (existing != null) {
            if (!sameAdjustment(existing, userId, operatorUserId, command, reason)) {
                throw error(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT);
            }
            return new DrawChanceAdjustmentView(accountView(account), ledgerView(existing));
        }

        long availableAfter;
        if (command.direction() == DrawChanceDirection.CREDIT) {
            try {
                availableAfter = Math.addExact(account.getAvailableBalance(), command.amount());
            } catch (ArithmeticException exception) {
                throw error(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID);
            }
        } else {
            if (account.getAvailableBalance() < command.amount()) {
                throw error(LotteryErrorCode.DRAW_CHANCE_INSUFFICIENT_AVAILABLE);
            }
            availableAfter = account.getAvailableBalance() - command.amount();
        }

        if (accounts.updateBalances(account.getId(), availableAfter, account.getReservedBalance()) != 1) {
            throw error(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID);
        }
        account.setAvailableBalance(availableAfter);

        DrawChanceLedgerRow ledger = new DrawChanceLedgerRow();
        ledger.setUserId(userId);
        ledger.setOperatorUserId(operatorUserId);
        ledger.setBusinessType(DrawChanceBusinessType.MANUAL_ADJUSTMENT);
        ledger.setBusinessId(command.adjustmentId().trim());
        ledger.setDirection(command.direction());
        ledger.setAmount(command.amount());
        ledger.setAvailableAfter(availableAfter);
        ledger.setReservedAfter(account.getReservedBalance());
        ledger.setReason(reason);
        try {
            if (ledgers.insertAdjustment(ledger) != 1) throw error(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID);
        } catch (DuplicateKeyException exception) {
            throw error(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT);
        }
        return new DrawChanceAdjustmentView(accountView(account), ledgerView(ledger));
    }

    private void validateAdjustment(long userId, long operatorUserId, AdjustDrawChanceCommand command) {
        if (userId <= 0 || operatorUserId <= 0 || command == null
                || command.adjustmentId() == null || command.adjustmentId().isBlank()
                || command.adjustmentId().trim().length() > 64 || command.direction() == null
                || command.amount() == null || command.amount() <= 0
                || command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 255) {
            throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        }
    }

    private void requireEnabledUser(long userId) {
        if (userId <= 0) throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        if (!Boolean.TRUE.equals(users.isEnabled(userId))) {
            throw error(LotteryErrorCode.DRAW_CHANCE_USER_UNAVAILABLE);
        }
    }

    private boolean sameAdjustment(DrawChanceLedgerRow existing, long userId, long operatorUserId,
                                   AdjustDrawChanceCommand command, String reason) {
        return Objects.equals(existing.getUserId(), userId)
                && Objects.equals(existing.getOperatorUserId(), operatorUserId)
                && existing.getDirection() == command.direction()
                && Objects.equals(existing.getAmount(), command.amount())
                && Objects.equals(existing.getReason(), reason);
    }

    private DrawChanceAccountView accountView(DrawChanceAccountRow row) {
        return new DrawChanceAccountView(row.getUserId(), row.getAvailableBalance(), row.getReservedBalance());
    }

    private DrawChanceLedgerView ledgerView(DrawChanceLedgerRow row) {
        return new DrawChanceLedgerView(row.getId(), row.getUserId(), row.getOperatorUserId(),
                row.getBusinessType(), row.getBusinessId(), row.getDirection(), row.getAmount(),
                row.getAvailableAfter(), row.getReservedAfter(), row.getReason(), row.getCreatedAt());
    }

    private LotteryDomainException error(LotteryErrorCode code) {
        return new LotteryDomainException(code);
    }
}
