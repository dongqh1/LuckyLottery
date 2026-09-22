package com.dongqh.luckyhub.reward;

import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.integration.hmall.HmallCatalogClient;
import com.dongqh.luckyhub.integration.hmall.HmallItemView;
import com.dongqh.luckyhub.membership.mapper.MembershipProductMapper;
import com.dongqh.luckyhub.reward.dto.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.mapper.RewardDefinitionMapper;
import com.dongqh.luckyhub.reward.service.impl.RewardDefinitionServiceImpl;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HmallProductRewardBindingTests {

    @Test
    void productRewardTargetsAnAvailableHmallItem() {
        RewardDefinitionMapper rewards = mock(RewardDefinitionMapper.class);
        HmallCatalogClient catalog = mock(HmallCatalogClient.class);
        CouponTemplateMapper coupons = mock(CouponTemplateMapper.class);
        MembershipProductMapper memberships = mock(MembershipProductMapper.class);
        when(catalog.getItem(1001L)).thenReturn(new HmallItemView(
                1001L, "商城耳机", 9900, 8, "/imgs/1001.jpg", "数码", "HMall", "黑色", 1));
        var service = new RewardDefinitionServiceImpl(
                rewards, catalog, coupons, memberships, new ObjectMapper());

        var created = service.create(new CreateRewardDefinitionCommand(
                "HMALL-ITEM-1001", "商城耳机奖励", RewardType.PRODUCT,
                1001L, 1L, null));

        assertThat(created.targetId()).isEqualTo(1001L);
        verify(catalog).getItem(1001L);
    }
}
