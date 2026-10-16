package com.dongqh.luckyhub.domain.lock.lottery;

public final class DrawLockKeys {
    private DrawLockKeys() {
    }

    public static String drawLock(long activityId, long userId) {
        return "draw:lock:" + activityId + ":" + userId;
    }
}
