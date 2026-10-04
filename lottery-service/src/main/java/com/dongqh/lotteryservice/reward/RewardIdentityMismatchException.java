package com.dongqh.lotteryservice.reward;

public class RewardIdentityMismatchException extends RuntimeException {
    private final RewardQuarantineReason reason;

    public RewardIdentityMismatchException(RewardQuarantineReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public RewardQuarantineReason reason() { return reason; }
}
