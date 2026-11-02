package com.dongqh.lottery.lock;

import com.dongqh.lottery.config.LotteryProperties;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.enums.LotteryErrorCode;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class DrawLockService {

    private final RedissonClient redissonClient;
    private final LotteryProperties properties;

    public DrawLockService(RedissonClient redissonClient, LotteryProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    public <T> T execute(long activityId, long userId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(DrawLockKeys.drawLock(activityId, userId));
        boolean acquired = false;
        try {
            acquired = lock.tryLock(properties.lockWait().toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired || !lock.isHeldByCurrentThread()) {
                throw new LotteryDomainException(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
            }
            return action.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LotteryDomainException(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
