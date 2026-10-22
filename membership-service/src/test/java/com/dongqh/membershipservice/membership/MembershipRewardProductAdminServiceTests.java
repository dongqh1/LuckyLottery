package com.dongqh.membershipservice.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.hmapi.dto.CreateMembershipRewardProductRequest;
import com.dongqh.membershipservice.domain.po.membership.MembershipProduct;
import com.dongqh.membershipservice.mapper.membership.MembershipProductMapper;
import com.dongqh.membershipservice.service.membership.MembershipRewardProductAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembershipRewardProductAdminServiceTests {
    private final MembershipProductMapper products = mock(MembershipProductMapper.class);
    private final MembershipRewardProductAdminService service = new MembershipRewardProductAdminService(products);

    @Test
    void createsNonPurchasableMembershipRewardWithUserProvidedLevelAndDays() {
        when(products.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(products.insert(any(MembershipProduct.class))).thenAnswer(call -> {
            ((MembershipProduct) call.getArgument(0)).setId(72L);
            return 1;
        });

        var result = service.create(new CreateMembershipRewardProductRequest(
                "LOTTERY-MEMBER-72", "抽奖会员礼", "PLATINUM", 14));

        ArgumentCaptor<MembershipProduct> inserted = ArgumentCaptor.forClass(MembershipProduct.class);
        verify(products).insert(inserted.capture());
        assertThat(inserted.getValue().getMembershipLevel()).isEqualTo("PLATINUM");
        assertThat(inserted.getValue().getDurationDays()).isEqualTo(14);
        assertThat(inserted.getValue().getPriceCent()).isZero();
        assertThat(inserted.getValue().getSalesEnabled()).isZero();
        assertThat(result.id()).isEqualTo(72L);
        assertThat(result.durationDays()).isEqualTo(14);
    }

    @Test
    void reusesSameRewardProductCodeWithoutCreatingADuplicate() {
        MembershipProduct existing = new MembershipProduct();
        existing.setId(72L);
        existing.setProductCode("LOTTERY-MEMBER-72");
        existing.setMembershipLevel("PLATINUM");
        existing.setDurationDays(14);
        existing.setStatus(1);
        existing.setSalesEnabled(0);
        when(products.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        var result = service.create(new CreateMembershipRewardProductRequest(
                "LOTTERY-MEMBER-72", "抽奖会员礼", "PLATINUM", 14));

        assertThat(result.id()).isEqualTo(72L);
        verify(products, never()).insert(any(MembershipProduct.class));
    }
}
