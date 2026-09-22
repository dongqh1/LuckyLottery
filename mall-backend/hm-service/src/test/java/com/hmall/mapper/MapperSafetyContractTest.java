package com.hmall.mapper;

import com.hmall.domain.dto.OrderDetailDTO;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MapperSafetyContractTest {

    @Test
    void balanceDeductionUsesBoundPositiveAmountAndSufficientBalanceGuard() throws Exception {
        Method method = UserMapper.class.getMethod("updateMoney", Long.class, Integer.class);
        String sql = normalize(method.getAnnotation(Update.class).value()[0]);

        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(sql).contains("balance - #{totalfee}");
        assertThat(sql).doesNotContain("${");
        assertThat(sql).contains("#{totalfee} > 0");
        assertThat(sql).contains("balance >= #{totalfee}");
    }

    @Test
    void stockDeductionRequiresPositiveQuantityAndSufficientStock() throws Exception {
        Method method = ItemMapper.class.getMethod("updateStock", OrderDetailDTO.class);
        String sql = normalize(method.getAnnotation(Update.class).value()[0]);

        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(sql).contains("stock - #{num}");
        assertThat(sql).contains("#{num} > 0");
        assertThat(sql).contains("stock >= #{num}");
    }

    private String normalize(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
