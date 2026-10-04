package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStep;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.exception.PointsDomainException;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Function;

@Service
public class PointsRedemptionStateService {

    private final PointsRedemptionOrderMapper orders;
    private final PointsRedemptionOfferService offers;

    public PointsRedemptionStateService(PointsRedemptionOrderMapper orders, PointsRedemptionOfferService offers) {
        this.orders = orders;
        this.offers = offers;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T withLockedOrder(String redemptionNo, Function<PointsRedemptionOrder, T> action) {
        Objects.requireNonNull(action, "action must not be null");
        return action.apply(requireLocked(redemptionNo));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reserveOfferStock(String redemptionNo, long offerId, int quantity) {
        PointsRedemptionOrder order = requireLocked(redemptionNo);
        if (!Objects.equals(order.getOfferId(), offerId) || !Objects.equals(order.getQuantity(), quantity)) {
            throw error(PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        }
        if (order.getProcessStep() != PointsRedemptionStep.CLAIMED) {
            if (order.getProcessStep() == PointsRedemptionStep.OFFER_RESERVED
                    || order.getProcessStep() == PointsRedemptionStep.POINTS_DEBITED
                    || order.getProcessStep() == PointsRedemptionStep.SKU_CONFIRMED
                    || order.getProcessStep() == PointsRedemptionStep.FULFILLMENT_ACCEPTED
                    || order.getProcessStep() == PointsRedemptionStep.COMPLETED) {
                return;
            }
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        offers.deductStock(offerId, quantity);
        if (orders.advanceStep(redemptionNo, PointsRedemptionStep.CLAIMED.name(),
                PointsRedemptionStep.OFFER_RESERVED.name()) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void compensateOfferStock(String redemptionNo) {
        PointsRedemptionOrder order = requireLocked(redemptionNo);
        if (order.getStatus() == PointsRedemptionStatus.COMPENSATED) {
            return;
        }
        if (order.getProcessStep() != PointsRedemptionStep.COMPENSATING_OFFER || order.getOfferId() == null) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        offers.restoreStock(order.getOfferId(), order.getQuantity());
        if (orders.compensate(redemptionNo, PointsRedemptionStep.COMPENSATING_OFFER.name(),
                order.getOriginalErrorCode(), order.getFailureReason()) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
    }

    private PointsRedemptionOrder requireLocked(String redemptionNo) {
        PointsRedemptionOrder order = orders.lockByRedemptionNo(redemptionNo);
        if (order == null) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        return order;
    }

    private PointsDomainException error(PointsErrorCode code) {
        return new PointsDomainException(code);
    }
}
