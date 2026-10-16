package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.enums.lottery.RewardQuarantineReason;

public class RewardIdentityMismatchException extends RuntimeException {
    private final RewardQuarantineReason reason;
    public RewardIdentityMismatchException(RewardQuarantineReason reason) {
        super(reason.name());
        this.reason = reason;
    }
    public RewardQuarantineReason reason() { return reason; }
}
