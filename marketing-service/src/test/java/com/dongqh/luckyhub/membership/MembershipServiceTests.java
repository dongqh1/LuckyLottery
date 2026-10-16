package com.dongqh.luckyhub.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.domain.dto.membership.*;
import com.dongqh.luckyhub.domain.po.membership.MembershipProduct;
import com.dongqh.luckyhub.enums.membership.MembershipCardType;
import com.dongqh.luckyhub.mapper.membership.MembershipGrantRecordMapper;
import com.dongqh.luckyhub.mapper.membership.MembershipProductMapper;
import com.dongqh.luckyhub.mapper.membership.UserMembershipMapper;
import com.dongqh.luckyhub.service.membership.MembershipService;
import com.dongqh.luckyhub.service.membership.impl.MembershipServiceImpl;
import com.dongqh.luckyhub.domain.vo.membership.MembershipProductView;
import com.dongqh.luckyhub.domain.po.rbac.SysUser;
import com.dongqh.luckyhub.mapper.rbac.SysUserMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@SpringBootTest class MembershipServiceTests {
 @Autowired MembershipService service;@Autowired JdbcTemplate jdbc;@Autowired SysUserMapper users;Long userId;
 @BeforeEach void setup(){jdbc.update("DELETE FROM membership_grant_record");jdbc.update("DELETE FROM user_membership");jdbc.update("DELETE FROM membership_product");SysUser u=new SysUser();u.setUsername("member-"+UUID.randomUUID());u.setPassword("x");u.setNickname("会员用户");u.setStatus(1);users.insert(u);userId=u.getId();}
 @AfterEach void clean(){jdbc.update("DELETE FROM membership_grant_record");jdbc.update("DELETE FROM user_membership");jdbc.update("DELETE FROM membership_product");users.deleteById(userId);}
 @Test void renewsFromExistingExpiryAndRepeatsBusinessIdWithoutExtension(){var p=service.createProduct(new CreateMembershipProductCommand("YEAR","年卡","MEMBER",MembershipCardType.YEAR,365,9900L,9000,2,12000));var first=service.purchase(new PurchaseMembershipCommand("BUY-Y1",p.id(),userId));var repeated=service.purchase(new PurchaseMembershipCommand("BUY-Y1",p.id(),userId));assertThat(repeated.expiresAt()).isEqualTo(first.expiresAt());var second=service.purchase(new PurchaseMembershipCommand("BUY-Y2",p.id(),userId));assertThat(Duration.between(first.expiresAt(),second.expiresAt()).toDays()).isEqualTo(365);assertThat(service.activeBenefit(userId)).isPresent().get().extracting(v->v.discountBasisPoints()).isEqualTo(9000);}
 @Test void rejectsBusinessReuseWithDifferentIdentity(){var p=service.createProduct(new CreateMembershipProductCommand("MONTH","月卡","MEMBER",MembershipCardType.MONTH,30,1000L,9500,1,10000));service.purchase(new PurchaseMembershipCommand("SAME",p.id(),userId));assertThatThrownBy(()->service.purchase(new PurchaseMembershipCommand("SAME",p.id()+1,userId))).hasMessageContaining("幂等");}

 @Test void listsOnlyEnabledProductsInStablePriceAndIdOrder() {
  MembershipProductMapper mapper = mock(MembershipProductMapper.class);
  MembershipService unit = new MembershipServiceImpl(
          mapper, mock(UserMembershipMapper.class), mock(MembershipGrantRecordMapper.class));
  when(mapper.selectList(any())).thenReturn(List.of(enabledYearly(), disabledMonthly(), enabledTrial()));

  assertThat(unit.listEnabledProducts())
          .extracting(MembershipProductView::status)
          .containsOnly(1);
  assertThat(unit.listEnabledProducts())
          .extracting(MembershipProductView::productCode)
          .containsExactly("TRIAL", "YEAR");

  @SuppressWarnings("unchecked")
  ArgumentCaptor<LambdaQueryWrapper<MembershipProduct>> query =
          ArgumentCaptor.forClass(LambdaQueryWrapper.class);
  verify(mapper, org.mockito.Mockito.times(2)).selectList(query.capture());
  String sqlSegment = query.getValue().getSqlSegment();
  assertThat(query.getValue().getParamNameValuePairs()).containsValue(1);
  assertThat(sqlSegment)
          .contains("price_cent ASC", "id ASC");
 }

 private MembershipProduct enabledYearly() { return product(2L, "YEAR", 9_900L, 1); }
 private MembershipProduct disabledMonthly() { return product(1L, "MONTH", 1_000L, 0); }
 private MembershipProduct enabledTrial() { return product(3L, "TRIAL", 0L, 1); }
 private MembershipProduct product(long id, String code, long priceCent, int status) {
  MembershipProduct product = new MembershipProduct();
  product.setId(id); product.setProductCode(code); product.setProductName(code);
  product.setMembershipLevel("MEMBER"); product.setCardType(MembershipCardType.MONTH);
  product.setDurationDays(30); product.setPriceCent(priceCent);
  product.setDiscountBasisPoints(10_000); product.setDailyDrawBonus(0);
  product.setPointsMultiplierBasisPoints(10_000); product.setStatus(status);
  return product;
 }
}
