package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.mapper.activity.MarketingActivityPrizeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LotteryPrizeMapperIntegrationTests {
    @Autowired MarketingActivityPrizeMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void disabledFirstHundredCannotHideLaterEnabledRowsAndResultIsOrderedAndBounded() {
        long activityId = insertActivity();
        for (int i = 1; i <= 101; i++) {
            insertRelation(activityId, insertPrize("禁用奖品" + i, 0), i, 1);
        }
        insertRelation(activityId, insertPrize("零库存奖品", 1), 0, 0);
        for (int i = 0; i < 105; i++) {
            insertRelation(activityId, insertPrize("启用奖品" + i, 1), 102 + i, 1);
        }

        var result = mapper.selectVisiblePrizeViews(activityId);

        assertThat(result).hasSize(100);
        assertThat(result).extracting(view -> view.prizeName())
                .containsExactlyElementsOf(IntStream.range(0, 100).mapToObj(i -> "启用奖品" + i).toList());
        assertThat(result).extracting(view -> view.sortOrder())
                .containsExactlyElementsOf(IntStream.range(102, 202).boxed().toList());
        assertThat(result).allSatisfy(view -> {
            assertThat(view.prizeId()).isPositive();
            assertThat(view.prizeType().name()).isEqualTo("PHYSICAL");
        });
    }

    private long insertActivity() {
        return insert("""
                INSERT INTO marketing_activity
                    (activity_name,status,start_time,end_time,daily_limit,no_win_weight,created_by)
                VALUES (?,?,?,?,?,?,?)
                """, "Task2 mapper contract", "RUNNING", LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1), 10, 1, 1L);
    }

    private long insertPrize(String name, int status) {
        return insert("""
                INSERT INTO marketing_prize(prize_name,prize_type,prize_level,status)
                VALUES (?, 'PHYSICAL', 'FIRST', ?)
                """, name, status);
    }

    private void insertRelation(long activityId, long prizeId, int sortOrder, int remainingStock) {
        jdbc.update("""
                INSERT INTO marketing_activity_prize
                    (activity_id,prize_id,weight,total_stock,remaining_stock,sort_order)
                VALUES (?,?,?,?,?,?)
                """, activityId, prizeId, 1, 1, remainingStock, sortOrder);
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
