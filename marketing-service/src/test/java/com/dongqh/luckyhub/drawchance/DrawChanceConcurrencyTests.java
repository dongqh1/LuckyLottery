package com.dongqh.luckyhub.drawchance;

import com.dongqh.luckyhub.domain.dto.drawchance.DrawChanceReservationCommand;
import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.activity.status-refresh-initial-delay=86400000",
        "luckyhub.lottery.reconcile-initial-delay=24h",
        "luckyhub.messaging.enabled=false",
        "luckyhub.lottery.outbox-initial-delay=24h"
})
class DrawChanceConcurrencyTests {
    @Autowired DrawChanceService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twentyConcurrentReservationsNeverOverdrawTenChances() throws Exception {
        String prefix = "p5-concurrent-" + UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status) VALUES(?,?,?,1)", prefix, "x", "并发测试");
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, prefix);
        try {
            service.credit(userId, prefix + "-reward", 10);
            var executor = Executors.newFixedThreadPool(10);
            try {
                var futures = java.util.stream.IntStream.range(0, 20).mapToObj(index -> executor.submit(() ->
                        service.reserve(new DrawChanceReservationCommand(prefix + "-" + index,
                                88L, userId, 1, LocalDate.of(2026, 8, 9))).bonusReserved())).toList();
                long reserved = 0;
                for (var future : futures) reserved += future.get();
                assertThat(reserved).isEqualTo(10);
            } finally {
                executor.shutdownNow();
            }
            var account = service.get(userId);
            assertThat(account.availableBalance()).isZero();
            assertThat(account.reservedBalance()).isEqualTo(10);
        } finally {
            jdbc.update("DELETE FROM draw_chance_ledger WHERE user_id=?", userId);
            jdbc.update("DELETE FROM draw_chance_reservation WHERE user_id=?", userId);
            jdbc.update("DELETE FROM draw_chance_account WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        }
    }

    @Test
    void mixedSingleAndTenReservationsNeverOverdrawTwentyFiveChances() throws Exception {
        String prefix = "mixed-concurrent-" + UUID.randomUUID();
        String username = "mix-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status) VALUES(?,?,?,1)", username, "x", "混合并发测试");
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        List<Integer> drawCounts = new ArrayList<>(List.of(10, 10));
        for (int i = 0; i < 20; i++) drawCounts.add(1);
        CountDownLatch ready = new CountDownLatch(drawCounts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            service.credit(userId, prefix + "-reward", 25);
            var executor = Executors.newFixedThreadPool(drawCounts.size());
            try {
                List<Future<Long>> futures = new ArrayList<>();
                for (int i = 0; i < drawCounts.size(); i++) {
                    int index = i;
                    int drawCount = drawCounts.get(i);
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("concurrency barrier timed out");
                        return service.reserve(new DrawChanceReservationCommand(prefix + "-" + index,
                                89L, userId, drawCount, LocalDate.of(2026, 8, 10))).bonusReserved();
                    }));
                }
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                long reserved = 0;
                for (Future<Long> future : futures) reserved += future.get(30, TimeUnit.SECONDS);
                assertThat(reserved).isEqualTo(25);
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
            var account = service.get(userId);
            assertThat(account.availableBalance()).isZero();
            assertThat(account.reservedBalance()).isEqualTo(25);
            assertThat(jdbc.queryForObject("""
                    SELECT COALESCE(SUM(bonus_reserved),0) FROM draw_chance_reservation
                    WHERE user_id=? AND activity_id=89
                    """, Long.class, userId)).isEqualTo(25L);
        } finally {
            jdbc.update("DELETE FROM draw_chance_ledger WHERE user_id=?", userId);
            jdbc.update("DELETE FROM draw_chance_reservation WHERE user_id=?", userId);
            jdbc.update("DELETE FROM draw_chance_account WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        }
    }
}
