package com.dongqh.luckyhub.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.coupon.dto.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.coupon.dto.IssueCouponCommand;
import com.dongqh.luckyhub.coupon.entity.CouponIssueRecord;
import com.dongqh.luckyhub.coupon.entity.CouponTemplate;
import com.dongqh.luckyhub.coupon.entity.UserCoupon;
import com.dongqh.luckyhub.coupon.enums.CouponErrorCode;
import com.dongqh.luckyhub.coupon.enums.CouponScopeType;
import com.dongqh.luckyhub.coupon.enums.CouponType;
import com.dongqh.luckyhub.coupon.enums.UserCouponStatus;
import com.dongqh.luckyhub.coupon.exception.CouponDomainException;
import com.dongqh.luckyhub.coupon.mapper.CouponIssueRecordMapper;
import com.dongqh.luckyhub.coupon.mapper.CouponTemplateMapper;
import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import com.dongqh.luckyhub.coupon.model.CouponLockSnapshot;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.coupon.service.port.CouponCatalogPort;
import com.dongqh.luckyhub.coupon.service.port.CouponItemSnapshot;
import com.dongqh.luckyhub.coupon.service.port.CouponUserEligibilityPort;
import com.dongqh.luckyhub.coupon.vo.CouponPageResponse;
import com.dongqh.luckyhub.coupon.vo.CouponTemplateView;
import com.dongqh.luckyhub.coupon.vo.UserCouponView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CouponServiceImpl implements CouponService {
    private final CouponTemplateMapper templates;
    private final UserCouponMapper coupons;
    private final CouponIssueRecordMapper issues;
    private final CouponUserEligibilityPort users;
    private final CouponCatalogPort catalog;

    public CouponServiceImpl(CouponTemplateMapper templates,
                             UserCouponMapper coupons,
                             CouponIssueRecordMapper issues,
                             CouponUserEligibilityPort users,
                             CouponCatalogPort catalog) {
        this.templates = templates;
        this.coupons = coupons;
        this.issues = issues;
        this.users = users;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public CouponTemplateView createTemplate(CreateCouponTemplateCommand command) {
        validate(command);
        CouponTemplate template = new CouponTemplate();
        template.setTemplateCode(command.templateCode().trim());
        template.setTemplateName(command.templateName().trim());
        template.setCouponType(command.couponType());
        template.setThresholdCent(command.thresholdCent());
        template.setDiscountCent(command.discountCent());
        template.setApplicableProductId(command.applicableProductId());
        template.setScopeType(command.scopeType());
        template.setHmallItemId(command.hmallItemId());
        template.setHmallCategory(normalizeCategory(command.hmallCategory()));
        template.setHmallBrand(normalizeCategory(command.hmallBrand()));
        template.setValidFrom(command.validFrom());
        template.setValidUntil(command.validUntil());
        template.setPerUserLimit(command.perUserLimit());
        template.setStackableWithMembership(command.stackableWithMembership());
        template.setStatus(1);
        try {
            templates.insert(template);
        } catch (DuplicateKeyException exception) {
            throw error(CouponErrorCode.TEMPLATE_DUPLICATE);
        }
        return view(template);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserCouponView issue(IssueCouponCommand command) {
        String businessNo = command.businessNo().trim();
        CouponIssueRecord existing = issues.selectOne(new LambdaQueryWrapper<CouponIssueRecord>()
                .eq(CouponIssueRecord::getBusinessNo, businessNo));
        if (existing != null) {
            return existingIssue(existing, command);
        }
        CouponTemplate template = templates.lockById(command.templateId());
        if (template == null || template.getStatus() != 1) {
            throw error(CouponErrorCode.TEMPLATE_NOT_FOUND);
        }
        if (!users.isEnabled(command.userId())) {
            throw error(CouponErrorCode.COUPON_UNAVAILABLE);
        }
        long count = issues.selectCount(new LambdaQueryWrapper<CouponIssueRecord>()
                .eq(CouponIssueRecord::getUserId, command.userId())
                .eq(CouponIssueRecord::getTemplateId, command.templateId()));
        if (count >= template.getPerUserLimit()) {
            throw error(CouponErrorCode.ISSUE_LIMIT);
        }
        UserCoupon coupon = new UserCoupon();
        coupon.setCouponNo(command.couponNo().trim());
        coupon.setTemplateId(template.getId());
        coupon.setUserId(command.userId());
        coupon.setStatus(UserCouponStatus.AVAILABLE);
        coupon.setValidFrom(template.getValidFrom());
        coupon.setValidUntil(template.getValidUntil());
        coupon.setVersion(0);
        CouponIssueRecord issue = new CouponIssueRecord();
        issue.setBusinessNo(businessNo);
        issue.setTemplateId(template.getId());
        issue.setUserId(command.userId());
        try {
            coupons.insert(coupon);
            issue.setUserCouponId(coupon.getId());
            issues.insert(issue);
        } catch (DuplicateKeyException exception) {
            throw error(CouponErrorCode.COUPON_IDEMPOTENCY_CONFLICT);
        }
        return view(coupon, template);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void issueReward(String fulfillmentNo, long userId, Long templateId,
                            String templateCode, int quantity) {
        if (fulfillmentNo == null || fulfillmentNo.isBlank() || fulfillmentNo.length() > 64
                || userId <= 0 || quantity <= 0 || quantity > 100
                || (templateId == null && (templateCode == null || templateCode.isBlank()))) {
            throw error(CouponErrorCode.COUPON_UNAVAILABLE);
        }
        CouponTemplate template = templateId == null
                ? templates.selectOne(new LambdaQueryWrapper<CouponTemplate>()
                        .eq(CouponTemplate::getTemplateCode, templateCode))
                : templates.selectById(templateId);
        if (template == null || (templateCode != null && !templateCode.isBlank()
                && !templateCode.equals(template.getTemplateCode()))) {
            throw error(CouponErrorCode.TEMPLATE_NOT_FOUND);
        }
        for (int index = 1; index <= quantity; index++) {
            String issueNo = "LR-C-" + UUID.nameUUIDFromBytes(
                    (fulfillmentNo + ":" + index).getBytes(StandardCharsets.UTF_8));
            issue(new IssueCouponCommand(issueNo, issueNo, template.getId(), userId));
        }
    }

    @Override
    public UserCouponView getMine(long userId, long couponId) {
        UserCoupon coupon = coupons.selectById(couponId);
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            throw error(CouponErrorCode.COUPON_NOT_FOUND);
        }
        return view(coupon, templates.selectById(coupon.getTemplateId()));
    }

    @Override
    public CouponPageResponse<UserCouponView> pageMine(long userId, long page, long size) {
        Page<UserCoupon> result = coupons.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getCreatedAt)
                        .orderByDesc(UserCoupon::getId));
        if (result.getRecords().isEmpty()) {
            return new CouponPageResponse<>(List.of(), result.getTotal(),
                    result.getCurrent(), result.getSize(), result.getPages());
        }
        Map<Long, CouponTemplate> byId = templates.selectBatchIds(result.getRecords().stream()
                        .map(UserCoupon::getTemplateId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
        return new CouponPageResponse<>(result.getRecords().stream()
                .map(coupon -> view(coupon, byId.get(coupon.getTemplateId())))
                .toList(), result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    @Transactional
    public CouponLockSnapshot lockForOrder(long userId, long couponId, String orderNo, Long productId,
                                          long amountAfterMembership, boolean hasMembership) {
        UserCoupon coupon = coupons.selectById(couponId);
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            throw error(CouponErrorCode.COUPON_NOT_FOUND);
        }
        CouponTemplate template = templates.selectById(coupon.getTemplateId());
        if (coupon.getStatus() == UserCouponStatus.LOCKED && orderNo.equals(coupon.getLockedOrderNo())) {
            return snapshot(coupon, template, amountAfterMembership, productId, hasMembership);
        }
        if (coupon.getStatus() != UserCouponStatus.AVAILABLE) {
            throw error(CouponErrorCode.COUPON_UNAVAILABLE);
        }
        validateApplicable(template, amountAfterMembership, productId, hasMembership);
        if (coupons.lock(couponId, userId, orderNo) != 1) {
            UserCoupon winner = coupons.selectById(couponId);
            if (winner != null && winner.getStatus() == UserCouponStatus.LOCKED
                    && orderNo.equals(winner.getLockedOrderNo())) {
                return snapshot(winner, template, amountAfterMembership, productId, hasMembership);
            }
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
        return snapshot(coupon, template, amountAfterMembership, productId, hasMembership);
    }

    @Override
    @Transactional
    public void useForOrder(long couponId, String orderNo) {
        UserCoupon coupon = coupons.selectById(couponId);
        if (coupon != null && coupon.getStatus() == UserCouponStatus.USED
                && orderNo.equals(coupon.getUsedOrderNo())) {
            return;
        }
        if (coupons.use(couponId, orderNo) != 1) {
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional
    public void releaseForOrder(long couponId, String orderNo) {
        UserCoupon coupon = coupons.selectById(couponId);
        if (coupon != null && coupon.getStatus() == UserCouponStatus.AVAILABLE) {
            return;
        }
        if (coupons.release(couponId, orderNo) != 1) {
            throw error(CouponErrorCode.COUPON_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional
    public int expireAvailable() {
        return coupons.expireAvailable();
    }

    private UserCouponView existingIssue(CouponIssueRecord issue, IssueCouponCommand command) {
        if (!issue.getTemplateId().equals(command.templateId()) || !issue.getUserId().equals(command.userId())) {
            throw error(CouponErrorCode.COUPON_IDEMPOTENCY_CONFLICT);
        }
        UserCoupon coupon = coupons.selectById(issue.getUserCouponId());
        if (!coupon.getCouponNo().equals(command.couponNo().trim())) {
            throw error(CouponErrorCode.COUPON_IDEMPOTENCY_CONFLICT);
        }
        return view(coupon, templates.selectById(coupon.getTemplateId()));
    }

    private CouponLockSnapshot snapshot(UserCoupon coupon, CouponTemplate template, long amount,
                                        Long productId, boolean member) {
        validateApplicable(template, amount, productId, member);
        return new CouponLockSnapshot(coupon.getId(), template.getId(), template.getTemplateName(),
                Math.min(template.getDiscountCent(), amount));
    }

    private void validateApplicable(CouponTemplate template, long amount, Long productId, boolean member) {
        LocalDateTime now = LocalDateTime.now();
        if (template == null || template.getStatus() != 1
                || now.isBefore(template.getValidFrom()) || !now.isBefore(template.getValidUntil())
                || amount < template.getThresholdCent()
                || (template.getApplicableProductId() != null
                && !template.getApplicableProductId().equals(productId))
                || (template.getScopeType() == CouponScopeType.ITEM
                && !template.getHmallItemId().equals(productId))
                || template.getScopeType() == CouponScopeType.CATEGORY
                || (member && !template.getStackableWithMembership())) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
    }

    private void validate(CreateCouponTemplateCommand command) {
        if (!command.validUntil().isAfter(command.validFrom())
                || (command.couponType() == CouponType.NO_THRESHOLD && command.thresholdCent() != 0)
                || (command.couponType() == CouponType.THRESHOLD && command.thresholdCent() <= 0)) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
        if (command.scopeType() == null) {
            return;
        }
        String category = normalizeCategory(command.hmallCategory());
        String brand = normalizeCategory(command.hmallBrand());
        if (command.applicableProductId() != null
                || (command.scopeType() == CouponScopeType.ALL
                && (command.hmallItemId() != null || category != null || brand != null))
                || (command.scopeType() == CouponScopeType.ITEM
                && (command.hmallItemId() == null || category != null || brand != null))
                || (command.scopeType() == CouponScopeType.CATEGORY
                && (category == null || command.hmallItemId() != null || brand != null))
                || (command.scopeType() == CouponScopeType.BRAND
                && (brand == null || command.hmallItemId() != null || category != null))) {
            throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
        }
        if (command.scopeType() == CouponScopeType.ITEM) {
            CouponItemSnapshot item = catalog.getItem(command.hmallItemId());
            if (item == null || !item.availableForReward()) {
                throw error(CouponErrorCode.COUPON_NOT_APPLICABLE);
            }
        }
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? null : category.trim();
    }

    private CouponTemplateView view(CouponTemplate template) {
        return new CouponTemplateView(template.getId(), template.getTemplateCode(), template.getTemplateName(),
                template.getCouponType(), template.getThresholdCent(), template.getDiscountCent(),
                template.getApplicableProductId(), template.getScopeType(), template.getHmallItemId(),
                template.getHmallCategory(), template.getHmallBrand(), template.getValidFrom(),
                template.getValidUntil(), template.getPerUserLimit(), template.getStackableWithMembership(),
                template.getStatus());
    }

    private UserCouponView view(UserCoupon coupon, CouponTemplate template) {
        return new UserCouponView(coupon.getId(), coupon.getCouponNo(), coupon.getTemplateId(),
                template == null ? null : template.getTemplateName(),
                template == null ? null : template.getCouponType(),
                template == null ? null : template.getThresholdCent(),
                template == null ? null : template.getDiscountCent(),
                template == null ? null : template.getScopeType(),
                template == null ? null : template.getHmallItemId(),
                template == null ? null : template.getHmallCategory(),
                template == null ? null : template.getHmallBrand(),
                coupon.getUserId(), coupon.getStatus(), coupon.getValidFrom(), coupon.getValidUntil(),
                coupon.getLockedOrderNo(), coupon.getUsedOrderNo());
    }

    private CouponDomainException error(CouponErrorCode code) {
        return new CouponDomainException(code);
    }
}
