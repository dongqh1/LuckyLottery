package com.dongqh.lottery.service.drawchance;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.lottery.domain.po.drawchance.DrawChanceAccountRow;
import com.dongqh.lottery.domain.po.drawchance.DrawChanceLedgerRow;
import com.dongqh.lottery.domain.po.drawchance.DrawChanceReservationRow;
import com.dongqh.lottery.enums.drawchance.DrawChanceBusinessType;
import com.dongqh.lottery.enums.drawchance.DrawChanceDirection;
import com.dongqh.lottery.enums.drawchance.DrawChanceReservationStatus;
import com.dongqh.lottery.mapper.drawchance.DrawChanceAccountMapper;
import com.dongqh.lottery.mapper.drawchance.DrawChanceLedgerMapper;
import com.dongqh.lottery.mapper.drawchance.DrawChanceReservationMapper;
import com.dongqh.lottery.domain.model.drawchance.DrawChanceReservationCommand;
import com.dongqh.lottery.domain.model.drawchance.DrawChanceReservationResult;
import com.dongqh.lottery.domain.vo.drawchance.DrawChanceAccountView;
import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.enums.LotteryErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class DrawChanceReservationService {
    private final DrawChanceAccountMapper accounts;
    private final DrawChanceLedgerMapper ledgers;
    private final DrawChanceReservationMapper reservations;
    private final DrawOrderMapper orders;
    private final UserClient users;

    @Autowired
    public DrawChanceReservationService(DrawChanceAccountMapper accounts, DrawChanceLedgerMapper ledgers,
                                       DrawChanceReservationMapper reservations, DrawOrderMapper orders,
                                       UserClient users) {
        this.accounts = accounts;
        this.ledgers = ledgers;
        this.reservations = reservations;
        this.orders = orders;
        this.users = users;
    }

    public DrawChanceReservationService(DrawChanceAccountMapper accounts, DrawChanceLedgerMapper ledgers,
                                       DrawChanceReservationMapper reservations, DrawOrderMapper orders) {
        this(accounts, ledgers, reservations, orders, null);
    }

    @Transactional
    public void credit(long userId, String businessId, long chances) {
        if (userId <= 0 || businessId == null || businessId.isBlank() || businessId.length() > 100 || chances <= 0) {
            throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        }
        if (users != null && !Boolean.TRUE.equals(users.isEnabled(userId))) {
            throw error(LotteryErrorCode.DRAW_CHANCE_USER_UNAVAILABLE);
        }
        DrawChanceAccountRow account = lockAccount(userId);
        DrawChanceLedgerRow existing = ledgers.selectBusiness(DrawChanceBusinessType.LOTTERY_REWARD, businessId);
        if (existing != null) {
            if (!Objects.equals(existing.getUserId(), userId) || !Objects.equals(existing.getAmount(), chances)
                    || existing.getDirection() != DrawChanceDirection.CREDIT) {
                throw error(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT);
            }
            return;
        }
        long available = exactAdd(account.getAvailableBalance(), chances);
        updateAccount(account, available, account.getReservedBalance());
        insertLedger(userId, DrawChanceBusinessType.LOTTERY_REWARD, businessId,
                DrawChanceDirection.CREDIT, chances, available, account.getReservedBalance());
    }

    public DrawChanceAccountView get(long userId) {
        if (userId <= 0) throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        DrawChanceAccountRow account = accounts.selectByUserId(userId);
        return account == null ? new DrawChanceAccountView(userId, 0, 0)
                : new DrawChanceAccountView(userId, account.getAvailableBalance(), account.getReservedBalance());
    }

    /**
     * 预留奖励抽奖次数。requestId 唯一约束负责幂等，账户行锁负责余额并发安全。
     */
    @Transactional
    public DrawChanceReservationResult reserve(DrawChanceReservationCommand command) {
        DrawChanceReservationRow candidate = new DrawChanceReservationRow();
        candidate.setRequestId(command.requestId());
        candidate.setUserId(command.userId());
        candidate.setActivityId(command.activityId());
        candidate.setDrawDate(command.drawDate());
        candidate.setDrawCount(command.drawCount());
        int inserted = reservations.insertIfAbsent(candidate);
        DrawChanceReservationRow reservation = reservations.lockByRequestId(command.requestId());
        requireSameIdentity(reservation, command);
        if (inserted == 0) {
            return result(reservation, true);
        }

        DrawChanceAccountRow account = lockAccount(command.userId());
        long bonus = Math.min(account.getAvailableBalance(), command.drawCount());
        if (bonus > 0) {
            updateAccount(account, account.getAvailableBalance() - bonus,
                    exactAdd(account.getReservedBalance(), bonus));
            if (reservations.setBonus(reservation.getId(), bonus) != 1) {
                throw balanceError();
            }
            reservation.setBonusReserved(bonus);
        }
        return result(reservation, false);
    }

    @Transactional
    public void confirm(String requestId) {
        settle(requestId, DrawChanceReservationStatus.CONFIRMED);
    }

    @Transactional
    public void release(String requestId) {
        settle(requestId, DrawChanceReservationStatus.RELEASED);
    }

    @Transactional
    public boolean confirmIfReserved(String requestId) {
        return settleIfPresent(requestId, DrawChanceReservationStatus.CONFIRMED);
    }

    @Transactional
    public boolean releaseIfReserved(String requestId) {
        return settleIfPresent(requestId, DrawChanceReservationStatus.RELEASED);
    }

    @Transactional
    public int reconcileExpired(int limit, LocalDateTime cutoff) {
        if (limit <= 0 || limit > 1000 || cutoff == null) {
            throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        }
        int settled = 0;
        for (DrawChanceReservationRow reservation : reservations.lockExpired(limit, cutoff)) {
            DrawOrderRow order = orders.selectByRequestIdForUpdate(reservation.getRequestId());
            if (order != null && order.getStatus() == DrawOrderStatus.SUCCESS) {
                settleLocked(reservation, DrawChanceReservationStatus.CONFIRMED);
                settled++;
            } else if (order != null && order.getStatus() == DrawOrderStatus.FAILED) {
                settleLocked(reservation, DrawChanceReservationStatus.RELEASED);
                settled++;
            } else if (order == null) {
                settleLocked(reservation, DrawChanceReservationStatus.RELEASED);
                settled++;
            }
        }
        return settled;
    }

    private void settle(String requestId, DrawChanceReservationStatus target) {
        if (!settleIfPresent(requestId, target)) {
            throw error(LotteryErrorCode.DRAW_CHANCE_RESERVATION_NOT_FOUND);
        }
    }

    private boolean settleIfPresent(String requestId, DrawChanceReservationStatus target) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw error(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST);
        }
        DrawChanceReservationRow reservation = reservations.lockByRequestId(requestId);
        if (reservation == null) {
            return false;
        }
        if (reservation.getStatus() == target) {
            return true;
        }
        if (reservation.getStatus() != DrawChanceReservationStatus.RESERVED) {
            throw error(LotteryErrorCode.DRAW_CHANCE_TERMINAL_STATE_CONFLICT);
        }
        settleLocked(reservation, target);
        return true;
    }

    private void settleLocked(DrawChanceReservationRow reservation, DrawChanceReservationStatus target) {
        long bonus = reservation.getBonusReserved();
        if (bonus > 0) {
            DrawChanceAccountRow account = lockAccount(reservation.getUserId());
            if (account.getReservedBalance() < bonus) {
                throw balanceError();
            }
            long available = account.getAvailableBalance();
            DrawChanceBusinessType type;
            DrawChanceDirection direction;
            if (target == DrawChanceReservationStatus.RELEASED) {
                available = exactAdd(available, bonus);
                type = DrawChanceBusinessType.DRAW_RELEASE;
                direction = DrawChanceDirection.CREDIT;
            } else {
                type = DrawChanceBusinessType.DRAW_CONSUME;
                direction = DrawChanceDirection.DEBIT;
            }
            long reserved = account.getReservedBalance() - bonus;
            updateAccount(account, available, reserved);
            insertLedger(reservation.getUserId(), type, reservation.getRequestId(), direction,
                    bonus, available, reserved);
        }
        if (reservations.settle(reservation.getId(), target) != 1) {
            throw balanceError();
        }
        reservation.setStatus(target);
    }

    private DrawChanceReservationResult result(DrawChanceReservationRow reservation, boolean duplicate) {
        long cumulative = reservations.sumActiveBonus(reservation.getUserId(),
                reservation.getActivityId(), reservation.getDrawDate());
        return new DrawChanceReservationResult(reservation.getRequestId(), reservation.getActivityId(),
                reservation.getUserId(), reservation.getDrawCount(), reservation.getDrawDate(),
                reservation.getBonusReserved(), cumulative, reservation.getStatus(), duplicate);
    }

    private void requireSameIdentity(DrawChanceReservationRow reservation, DrawChanceReservationCommand command) {
        if (reservation == null || !Objects.equals(reservation.getUserId(), command.userId())
                || !Objects.equals(reservation.getActivityId(), command.activityId())
                || !Objects.equals(reservation.getDrawCount(), command.drawCount())
                || !Objects.equals(reservation.getDrawDate(), command.drawDate())) {
            throw error(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT);
        }
    }

    private DrawChanceAccountRow lockAccount(long userId) {
        accounts.insertIfAbsent(userId);
        DrawChanceAccountRow account = accounts.lockByUserId(userId);
        if (account == null) {
            throw balanceError();
        }
        return account;
    }

    private void updateAccount(DrawChanceAccountRow account, long available, long reserved) {
        if (available < 0 || reserved < 0
                || accounts.updateBalances(account.getId(), available, reserved) != 1) {
            throw balanceError();
        }
        account.setAvailableBalance(available);
        account.setReservedBalance(reserved);
    }

    private void insertLedger(long userId, DrawChanceBusinessType type, String businessId,
                              DrawChanceDirection direction, long amount, long available, long reserved) {
        DrawChanceLedgerRow ledger = new DrawChanceLedgerRow();
        ledger.setUserId(userId);
        ledger.setBusinessType(type);
        ledger.setBusinessId(businessId);
        ledger.setDirection(direction);
        ledger.setAmount(amount);
        ledger.setAvailableAfter(available);
        ledger.setReservedAfter(reserved);
        ledgers.insert(ledger);
    }

    private long exactAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw balanceError();
        }
    }

    private LotteryDomainException balanceError() {
        return error(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID);
    }

    private LotteryDomainException error(LotteryErrorCode code) {
        return new LotteryDomainException(code);
    }
}
