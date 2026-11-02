package com.dongqh.lottery.activityprize;

import com.dongqh.lottery.domain.dto.activityprize.AddActivityPrizeCommand;
import com.dongqh.lottery.domain.dto.activityprize.UpdateActivityPrizeCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPrizeCommandContractTests {

    @Test
    void addCommandKeepsLegacyConstraintMessages() throws NoSuchFieldException {
        assertConstraint(AddActivityPrizeCommand.class, "prizeId", NotNull.class, "奖品ID不能为空");
        assertConstraint(AddActivityPrizeCommand.class, "prizeId", Positive.class, "奖品ID必须大于0");
        assertConstraint(AddActivityPrizeCommand.class, "weight", NotNull.class, "中奖权重不能为空");
        assertConstraint(AddActivityPrizeCommand.class, "weight", Positive.class, "中奖权重必须大于0");
        assertConstraint(AddActivityPrizeCommand.class, "totalStock", NotNull.class, "总库存不能为空");
        assertConstraint(AddActivityPrizeCommand.class, "totalStock", Positive.class, "总库存必须大于0");
        assertConstraint(AddActivityPrizeCommand.class, "sortOrder", NotNull.class, "展示顺序不能为空");
        assertConstraint(AddActivityPrizeCommand.class, "sortOrder", PositiveOrZero.class, "展示顺序不能小于0");
    }

    @Test
    void updateCommandKeepsLegacyConstraintMessages() throws NoSuchFieldException {
        assertConstraint(UpdateActivityPrizeCommand.class, "weight", NotNull.class, "中奖权重不能为空");
        assertConstraint(UpdateActivityPrizeCommand.class, "weight", Positive.class, "中奖权重必须大于0");
        assertConstraint(UpdateActivityPrizeCommand.class, "totalStock", NotNull.class, "总库存不能为空");
        assertConstraint(UpdateActivityPrizeCommand.class, "totalStock", Positive.class, "总库存必须大于0");
        assertConstraint(UpdateActivityPrizeCommand.class, "sortOrder", NotNull.class, "展示顺序不能为空");
        assertConstraint(UpdateActivityPrizeCommand.class, "sortOrder", PositiveOrZero.class, "展示顺序不能小于0");
    }

    private <A extends java.lang.annotation.Annotation> void assertConstraint(
            Class<?> command, String fieldName, Class<A> annotationType, String message) throws NoSuchFieldException {
        Field field = command.getDeclaredField(fieldName);
        A annotation = field.getAnnotation(annotationType);
        assertThat(annotation).as(command.getSimpleName() + "." + fieldName).isNotNull();
        String actual = annotation instanceof NotNull notNull ? notNull.message()
                : annotation instanceof Positive positive ? positive.message()
                : ((PositiveOrZero) annotation).message();
        assertThat(actual).isEqualTo(message);
    }
}
