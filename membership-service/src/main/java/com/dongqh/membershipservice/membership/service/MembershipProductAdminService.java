package com.dongqh.membershipservice.membership.service;

import com.dongqh.membershipservice.membership.domain.MembershipDomainException;
import com.dongqh.membershipservice.membership.dto.CreateMembershipProductCommand;
import com.dongqh.membershipservice.membership.entity.MembershipProduct;
import com.dongqh.membershipservice.membership.enums.MembershipCardType;
import com.dongqh.membershipservice.membership.enums.MembershipErrorCode;
import com.dongqh.membershipservice.membership.mapper.MembershipProductMapper;
import com.dongqh.membershipservice.membership.vo.MembershipProductView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipProductAdminService {
    private final MembershipProductMapper products;

    public MembershipProductAdminService(MembershipProductMapper products) {
        this.products = products;
    }

    @Transactional
    public MembershipProductView create(CreateMembershipProductCommand command) {
        if (command == null || command.cardType() == null
                || command.durationDays() != durationDays(command.cardType())) {
            throw error(MembershipErrorCode.PRODUCT_INVALID);
        }
        MembershipProduct product = new MembershipProduct();
        product.setProductCode(command.productCode().trim());
        product.setProductName(command.productName().trim());
        product.setMembershipLevel(command.membershipLevel().trim());
        product.setCardType(command.cardType());
        product.setDurationDays(command.durationDays());
        product.setPriceCent(command.priceCent());
        product.setDiscountBasisPoints(command.discountBasisPoints());
        product.setDailyDrawBonus(command.dailyDrawBonus());
        product.setPointsMultiplierBasisPoints(command.pointsMultiplierBasisPoints());
        product.setStatus(1);
        try {
            products.insert(product);
        } catch (DuplicateKeyException exception) {
            throw error(MembershipErrorCode.PRODUCT_DUPLICATE);
        }
        return new MembershipProductView(product.getId(), product.getProductCode(), product.getProductName(),
                product.getMembershipLevel(), product.getCardType(), product.getDurationDays(), product.getPriceCent(),
                product.getDiscountBasisPoints(), product.getDailyDrawBonus(),
                product.getPointsMultiplierBasisPoints(), product.getStatus());
    }

    private int durationDays(MembershipCardType cardType) {
        return switch (cardType) {
            case MONTH -> 30;
            case QUARTER -> 90;
            case YEAR -> 365;
        };
    }

    private MembershipDomainException error(MembershipErrorCode code) {
        return new MembershipDomainException(code);
    }
}
