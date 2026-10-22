package com.dongqh.lotteryservice.prizecatalog;

import com.dongqh.lotteryservice.enums.prizecatalog.PrizeErrorCode;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeLevel;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.domain.model.prizecatalog.RewardPrizeTypeMapping;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreatePrizeCommand;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreateRewardDefinitionCommand;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.PrizeQuery;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.UpdatePrizeCommand;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PageResponse;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PrizeView;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrizeCatalogContractTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void preservesEveryRewardToPrizeTypeMapping() {
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.PRODUCT)).isEqualTo(PrizeType.PHYSICAL);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.COUPON)).isEqualTo(PrizeType.COUPON);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.POINTS)).isEqualTo(PrizeType.POINTS);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.MEMBERSHIP)).isEqualTo(PrizeType.MEMBERSHIP);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.DRAW_CHANCE)).isEqualTo(PrizeType.DRAW_CHANCE);
        assertThat(RewardPrizeTypeMapping.matches(null, PrizeType.COUPON)).isFalse();
        assertThat(RewardPrizeTypeMapping.matches(RewardType.COUPON, null)).isFalse();
        assertThatThrownBy(() -> RewardPrizeTypeMapping.toPrizeType(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("奖励类型不能为空");
    }

    @Test
    void prizeCreationRequestContainsRewardDetailsWithoutRewardDefinitionId() {
        Set<String> fields = Arrays.stream(CreatePrizeCommand.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("prizeName", "rewardType", "prizeLevel", "imageUrl",
                "description", "stackable", "targetId", "quantity", "membershipProductCode",
                "membershipLevel", "durationDays")
                .doesNotContain("rewardDefinitionId", "prizeType", "configSnapshot");
    }

    @Test
    void rewardDefinitionRequestDoesNotExposeConfigurationJson() {
        Set<String> fields = Arrays.stream(CreateRewardDefinitionCommand.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("rewardCode", "rewardName", "rewardType", "targetId", "quantity")
                .doesNotContain("configSnapshot");
    }

    @Test
    void prizeUpdateRequestDoesNotExposeRewardDefinitionId() {
        Set<String> fields = Arrays.stream(UpdatePrizeCommand.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(fields).doesNotContain("rewardDefinitionId");
        assertThat(fields).containsExactlyInAnyOrder("prizeName", "prizeType", "prizeLevel", "imageUrl",
                "description", "stackable");
    }

    @Test
    void prizeManagementResponseHidesInternalRewardDefinitionId() {
        Set<String> fields = Arrays.stream(PrizeView.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(fields).doesNotContain("rewardDefinitionId");
        assertThat(fields).contains("rewardType", "targetId", "quantity");
    }

    @Test
    void preservesPrizeErrorCodesMessagesAndStatuses() {
        assertError(PrizeErrorCode.PRIZE_NOT_FOUND, 41001, "奖品不存在", HttpStatus.NOT_FOUND);
        assertError(PrizeErrorCode.IMAGE_EMPTY, 41002, "奖品图片不能为空", HttpStatus.BAD_REQUEST);
        assertError(PrizeErrorCode.IMAGE_TYPE_UNSUPPORTED, 41003, "仅支持 JPEG、PNG 和 WebP 图片", HttpStatus.BAD_REQUEST);
        assertError(PrizeErrorCode.IMAGE_TOO_LARGE, 41004, "奖品图片不能超过5 MiB", HttpStatus.CONTENT_TOO_LARGE);
        assertError(PrizeErrorCode.OSS_UPLOAD_FAILED, 51001, "奖品图片上传失败", HttpStatus.BAD_GATEWAY);
        assertError(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE, 51002, "对象存储尚未配置", HttpStatus.SERVICE_UNAVAILABLE);
        assertError(PrizeErrorCode.REWARD_BINDING_INVALID, 41005, "统一奖励绑定不合法", HttpStatus.BAD_REQUEST);
    }

    @Test
    void validatesSinglePrizeAndFulfillmentRequestWithoutRewardId() {
        var create = new CreatePrizeCommand("", null, null, "x".repeat(501), "x".repeat(501), null,
                0L, 0L, "x".repeat(65), "x".repeat(31), 0);
        assertThat(validator.validate(create)).extracting(v -> v.getMessage()).containsExactlyInAnyOrder(
                "奖品名称不能为空", "奖励类型不能为空", "奖品等级不能为空",
                "奖品图片地址不能超过500个字符", "奖品说明不能超过500个字符",
                "是否可叠加不能为空", "目标 ID 必须大于0", "发放数量必须大于0",
                "会员奖励编号不能超过64个字符", "会员等级不能超过30个字符", "会员天数必须大于0");
        var update = new UpdatePrizeCommand("", null, null, "x".repeat(501), "x".repeat(501), null);
        assertThat(validator.validate(update)).extracting(v -> v.getMessage()).containsExactlyInAnyOrder(
                "奖品名称不能为空", "奖品类型不能为空", "奖品等级不能为空",
                "奖品图片地址不能超过500个字符", "奖品说明不能超过500个字符",
                "是否可叠加不能为空");
        assertThat(validator.validate(new CreatePrizeCommand("x", RewardType.COUPON,
                PrizeLevel.FIRST, null, null, true, null, 1L))).isEmpty();
    }

    @Test
    void preservesQueryDefaultsValidationAndPageEnvelope() {
        PrizeQuery query = new PrizeQuery();
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(20);
        assertThat(validator.validate(query)).isEmpty();
        query.setPage(0);
        query.setSize(101);
        query.setStatus(2);
        assertThat(validator.validate(query)).extracting(v -> v.getMessage())
                .containsExactlyInAnyOrder("页码不能小于1", "每页数量不能超过100", "奖品状态只能是0或1");
        PageResponse<String> response = new PageResponse<>(List.of("a"), 21, 2, 20, 2);
        assertThat(response.records()).containsExactly("a");
        assertThat(response.total()).isEqualTo(21);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.pages()).isEqualTo(2);
    }

    private void assertError(PrizeErrorCode error, int code, String message, HttpStatus status) {
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.message()).isEqualTo(message);
        assertThat(error.httpStatus()).isEqualTo(status);
    }
}
