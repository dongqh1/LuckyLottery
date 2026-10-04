package com.dongqh.lotteryservice.lock;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.quota.DrawQuotaKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class RedissonDrawLockService implements DrawLockService {

    private final RedissonClient redissonClient;
    private final LotteryProperties properties;

    public RedissonDrawLockService(RedissonClient redissonClient, LotteryProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @Override
    public <T> T execute(long activityId, long userId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(DrawQuotaKeys.drawLock(activityId, userId));
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
