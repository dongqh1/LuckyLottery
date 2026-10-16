package com.dongqh.membershipservice.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.dongqh.membershipservice.domain.po.membership.MembershipProduct;
import com.dongqh.membershipservice.domain.po.membership.UserMembership;
import com.dongqh.membershipservice.enums.membership.MembershipCardType;
import com.dongqh.membershipservice.mapper.membership.MembershipProductMapper;
import com.dongqh.membershipservice.mapper.membership.UserMembershipMapper;
import com.dongqh.membershipservice.service.membership.MembershipQueryService;
import com.dongqh.membershipservice.domain.vo.membership.MembershipProductView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeAll;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembershipQueryServiceTests {

    @BeforeAll
    static void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "unit-test");
        TableInfoHelper.initTableInfo(assistant, MembershipProduct.class);
        TableInfoHelper.initTableInfo(assistant, UserMembership.class);
    }

    private final MembershipProductMapper products = mock(MembershipProductMapper.class);
    private final UserMembershipMapper memberships = mock(UserMembershipMapper.class);
    private final MembershipQueryService service = new MembershipQueryService(products, memberships);

    @Test
    void returnsOnlyEnabledProductsInStablePriceThenIdOrderWithLimit() {
        when(products.selectList(any())).thenReturn(List.of(
                product(2L, "YEAR", 9_900L, 1),
                product(1L, "DISABLED", 500L, 0),
                product(3L, "TRIAL", 0L, 1)));

        List<MembershipProductView> result = service.listEnabledProducts();

        assertThat(result).extracting(MembershipProductView::productCode).containsExactly("TRIAL", "YEAR");
        assertThat(result).extracting(MembershipProductView::status).containsOnly(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<MembershipProduct>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(products).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("status =", "price_cent ASC", "id ASC", "LIMIT 100");
    }

    @Test
    void activeBenefitRequiresAnUnexpiredMembershipAndPreservesBenefitFields() {
        LocalDateTime now = LocalDateTime.parse("2026-09-27T10:00:00.123");
        UserMembership membership = new UserMembership();
        membership.setMembershipLevel("GOLD");
        membership.setDiscountBasisPoints(9_000);
        membership.setDailyDrawBonus(2);
        membership.setPointsMultiplierBasisPoints(12_000);
        membership.setExpiresAt(now.plusDays(1));
        when(memberships.selectOne(any())).thenReturn(membership);

        assertThat(service.activeBenefit(101L, now)).isPresent().get()
                .satisfies(benefit -> {
                    assertThat(benefit.membershipLevel()).isEqualTo("GOLD");
                    assertThat(benefit.discountBasisPoints()).isEqualTo(9_000);
                    assertThat(benefit.dailyDrawBonus()).isEqualTo(2);
                    assertThat(benefit.pointsMultiplierBasisPoints()).isEqualTo(12_000);
                    assertThat(benefit.expiresAt()).isEqualTo(now.plusDays(1));
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<UserMembership>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(memberships).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("user_id =", "expires_at >");
    }

    @Test
    void absentMembershipReturnsEmpty() {
        when(memberships.selectOne(any())).thenReturn(null);

        assertThat(service.getMine(101L)).isEmpty();
        assertThat(service.activeBenefit(101L, LocalDateTime.now())).isEmpty();
    }

    private MembershipProduct product(long id, String code, long priceCent, int status) {
        MembershipProduct product = new MembershipProduct();
        product.setId(id);
        product.setProductCode(code);
        product.setProductName(code);
        product.setMembershipLevel("GOLD");
        product.setCardType(MembershipCardType.MONTH);
        product.setDurationDays(30);
        product.setPriceCent(priceCent);
        product.setDiscountBasisPoints(10_000);
        product.setDailyDrawBonus(0);
        product.setPointsMultiplierBasisPoints(10_000);
        product.setStatus(status);
        return product;
    }
}
