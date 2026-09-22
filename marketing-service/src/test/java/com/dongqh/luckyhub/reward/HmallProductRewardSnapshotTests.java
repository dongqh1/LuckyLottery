package com.dongqh.luckyhub.reward;

import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.integration.hmall.HmallCatalogClient;
import com.dongqh.luckyhub.integration.hmall.HmallItemView;
import com.dongqh.luckyhub.membership.mapper.MembershipProductMapper;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.entity.RewardDefinition;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.mapper.RewardDefinitionMapper;
import com.dongqh.luckyhub.reward.service.impl.RewardSnapshotServiceImpl;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HmallProductRewardSnapshotTests {

    @Test
    void freezesHmallItemIdentityAndDisplayData() {
        RewardDefinitionMapper rewards = mock(RewardDefinitionMapper.class);
        CouponTemplateMapper coupons = mock(CouponTemplateMapper.class);
        MembershipProductMapper memberships = mock(MembershipProductMapper.class);
        HmallCatalogClient catalog = mock(HmallCatalogClient.class);
        RewardDefinition definition = new RewardDefinition();
        definition.setId(41L);
        definition.setRewardCode("HMALL-ITEM-1001");
        definition.setRewardName("商城耳机奖励");
        definition.setRewardType(RewardType.PRODUCT);
        definition.setTargetId(1001L);
        definition.setQuantity(1L);
        definition.setStatus(1);
        when(rewards.selectByIds(anyCollection())).thenReturn(List.of(definition));
        when(catalog.getItem(1001L)).thenReturn(new HmallItemView(
                1001L, "商城耳机", 9900, 8, "/imgs/1001.jpg", "数码", "HMall", "黑色", 1));
        MarketingPrize prize = new MarketingPrize();
        prize.setId(71L);
        prize.setRewardDefinitionId(41L);
        prize.setPrizeType(PrizeType.PHYSICAL);
        prize.setStatus(1);
        var service = new RewardSnapshotServiceImpl(
                rewards, coupons, memberships, catalog, new ObjectMapper());

        String payload = service.resolveForPrizes(List.of(prize)).get(71L).payloadJson();

        assertThat(payload).isEqualTo("{\"itemId\":1001,\"itemName\":\"商城耳机\","
                + "\"itemImage\":\"/imgs/1001.jpg\",\"itemPriceCent\":9900,\"quantity\":1}");
        assertThat(payload).doesNotContain("skuId", "skuCode");
    }
}
