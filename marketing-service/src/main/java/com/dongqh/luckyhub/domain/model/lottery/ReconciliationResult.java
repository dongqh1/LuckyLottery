package com.dongqh.luckyhub.domain.model.lottery;

/** A compact audit summary for one bounded reconciliation run. */
public record ReconciliationResult(
        int scanned,
        int confirmed,
        int released,
        int timedOut,
        int deferred,
        int failed
) {
}
