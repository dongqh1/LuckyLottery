package com.dongqh.membership.service.membership;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.MembershipPurchaseSnapshotDTO;
import com.dongqh.hmapi.dto.OrderDomain;
import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.membership.domain.po.membership.MembershipGrantRecord;
import com.dongqh.membership.domain.po.membership.MembershipProduct;
import com.dongqh.membership.domain.po.membership.UserMembership;
import com.dongqh.membership.mapper.membership.MembershipGrantRecordMapper;
import com.dongqh.membership.mapper.membership.MembershipProductMapper;
import com.dongqh.membership.mapper.membership.UserMembershipMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class MembershipRewardGrantService {
    private final MembershipProductMapper products;
    private final UserMembershipMapper memberships;
    private final MembershipGrantRecordMapper grants;
    private final UserClient users;

    public MembershipRewardGrantService(MembershipProductMapper products, UserMembershipMapper memberships,
            MembershipGrantRecordMapper grants, UserClient users) {
        this.products = products;
        this.memberships = memberships;
        this.grants = grants;
        this.users = users;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void grant(String fulfillmentNo, long userId, Long productId, String productCode,
                      int durationDays, int quantity) {
        if (fulfillmentNo == null || fulfillmentNo.isBlank() || fulfillmentNo.length() > 64
                || userId <= 0 || (productId == null && (productCode == null || productCode.isBlank()))
                || (productId != null && productId <= 0) || durationDays <= 0
                || quantity <= 0 || quantity > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会员奖励参数无效");
        }
        if (!Boolean.TRUE.equals(users.isEnabled(userId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员奖励用户不可用");
        }
        MembershipProduct product = productId == null
                ? products.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MembershipProduct>()
                        .eq(MembershipProduct::getProductCode, productCode))
                : products.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())
                || (productCode != null && !productCode.isBlank()
                && !productCode.equals(product.getProductCode()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员奖励产品不可用");
        }

        for (int index = 1; index <= quantity; index++) {
            String businessNo = "LR-M-" + UUID.nameUUIDFromBytes(
                    (fulfillmentNo + ":" + index).getBytes(StandardCharsets.UTF_8));
            MembershipGrantRecord existing = grants.selectByBusinessNoForUpdate(businessNo);
            if (existing != null) {
                if (!Objects.equals(existing.getUserId(), userId)
                        || !Objects.equals(existing.getMembershipProductId(), product.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "会员奖励幂等键冲突");
                }
                continue;
            }
            grantOne(businessNo, userId, product, durationDays, LocalDateTime.now());
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void grantPaidPurchase(PaySuccessMessage event) {
        if (event == null || event.orderDomain() != OrderDomain.MEMBERSHIP
                || event.membershipPurchase() == null || event.couponId() != null
                || event.amount().longValue() != event.membershipPurchase().priceCent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员支付事件无效");
        }
        MembershipPurchaseSnapshotDTO snapshot = event.membershipPurchase();
        String businessNo = "PAY-M-" + event.orderId();
        MembershipGrantRecord existing = grants.selectByBusinessNoForUpdate(businessNo);
        if (existing != null) {
            long grantedDays = ChronoUnit.DAYS.between(existing.getStartsAt(), existing.getExpiresAt());
            if (!Objects.equals(existing.getUserId(), event.userId())
                    || !Objects.equals(existing.getMembershipProductId(), snapshot.productId())
                    || grantedDays != snapshot.durationDays()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "会员购买幂等键冲突");
            }
            return;
        }
        MembershipProduct product = new MembershipProduct();
        product.setId(snapshot.productId());
        product.setProductCode(snapshot.productCode());
        product.setProductName(snapshot.productName());
        product.setMembershipLevel(snapshot.membershipLevel());
        product.setDurationDays(snapshot.durationDays());
        product.setPriceCent(snapshot.priceCent());
        product.setDiscountBasisPoints(snapshot.discountBasisPoints());
        product.setDailyDrawBonus(snapshot.dailyDrawBonus());
        product.setPointsMultiplierBasisPoints(snapshot.pointsMultiplierBasisPoints());
        product.setStatus(1);
        grantOne(businessNo, event.userId(), product, snapshot.durationDays(), event.paidAt());
    }

    private void grantOne(String businessNo, long userId, MembershipProduct product, int durationDays,
                          LocalDateTime effectiveAt) {
        LocalDateTime now = effectiveAt.truncatedTo(ChronoUnit.MILLIS);
        UserMembership membership = memberships.selectByUserIdForUpdate(userId);
        LocalDateTime startsAt = now;
        LocalDateTime base = now;
        if (membership != null && membership.getMembershipLevel().equals(product.getMembershipLevel())
                && membership.getExpiresAt().isAfter(now)) {
            startsAt = membership.getStartsAt();
            base = membership.getExpiresAt();
        }
        LocalDateTime expiresAt = base.plusDays(durationDays);
        if (membership == null) {
            membership = new UserMembership();
            membership.setUserId(userId);
            membership.setStartsAt(startsAt);
            membership.setVersion(0);
        } else {
            membership.setVersion(membership.getVersion() + 1);
        }
        membership.setMembershipLevel(product.getMembershipLevel());
        membership.setExpiresAt(expiresAt);
        membership.setDiscountBasisPoints(product.getDiscountBasisPoints());
        membership.setDailyDrawBonus(product.getDailyDrawBonus());
        membership.setPointsMultiplierBasisPoints(product.getPointsMultiplierBasisPoints());
        if (membership.getId() == null) memberships.insert(membership);
        else memberships.updateById(membership);

        MembershipGrantRecord grant = new MembershipGrantRecord();
        grant.setBusinessNo(businessNo);
        grant.setUserId(userId);
        grant.setMembershipProductId(product.getId());
        grant.setStartsAt(base);
        grant.setExpiresAt(expiresAt);
        grants.insert(grant);
    }
}
