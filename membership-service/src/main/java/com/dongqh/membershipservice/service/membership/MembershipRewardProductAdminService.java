package com.dongqh.membershipservice.service.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.hmapi.dto.CreateMembershipRewardProductRequest;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.membershipservice.domain.po.membership.MembershipProduct;
import com.dongqh.membershipservice.mapper.membership.MembershipProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@Service
public class MembershipRewardProductAdminService {
    private final MembershipProductMapper products;

    public MembershipRewardProductAdminService(MembershipProductMapper products) {
        this.products = products;
    }

    @Transactional
    public MembershipRewardProductDTO create(CreateMembershipRewardProductRequest command) {
        if (command == null || !StringUtils.hasText(command.productCode())
                || command.productCode().trim().length() > 64
                || !StringUtils.hasText(command.productName()) || command.productName().trim().length() > 100
                || !StringUtils.hasText(command.membershipLevel()) || command.membershipLevel().trim().length() > 30
                || command.durationDays() == null || command.durationDays() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会员奖励类型和有效天数不能为空");
        }
        MembershipProduct existing = findByCode(command.productCode().trim());
        if (existing != null) return reuseOrConflict(existing, command);

        MembershipProduct product = new MembershipProduct();
        product.setProductCode(command.productCode().trim());
        product.setProductName(command.productName().trim());
        product.setMembershipLevel(command.membershipLevel().trim());
        product.setCardType(com.dongqh.membershipservice.enums.membership.MembershipCardType.MONTH);
        product.setDurationDays(command.durationDays());
        product.setPriceCent(0L);
        product.setDiscountBasisPoints(10_000);
        product.setDailyDrawBonus(0);
        product.setPointsMultiplierBasisPoints(10_000);
        product.setStatus(1);
        product.setSalesEnabled(0);
        try {
            products.insert(product);
        } catch (DuplicateKeyException duplicate) {
            MembershipProduct concurrent = findByCode(command.productCode().trim());
            if (concurrent != null) return reuseOrConflict(concurrent, command);
            throw duplicate;
        }
        return view(product);
    }

    private MembershipProduct findByCode(String productCode) {
        return products.selectOne(new LambdaQueryWrapper<MembershipProduct>()
                .eq(MembershipProduct::getProductCode, productCode));
    }

    private MembershipRewardProductDTO reuseOrConflict(MembershipProduct product,
                                                        CreateMembershipRewardProductRequest command) {
        if (!Integer.valueOf(1).equals(product.getStatus())
                || !Integer.valueOf(0).equals(product.getSalesEnabled())
                || !command.membershipLevel().trim().equals(product.getMembershipLevel())
                || !command.durationDays().equals(product.getDurationDays())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员奖励编号已用于其他配置");
        }
        return view(product);
    }

    private MembershipRewardProductDTO view(MembershipProduct product) {
        return new MembershipRewardProductDTO(product.getId(), product.getProductCode(),
                product.getMembershipLevel(), product.getDurationDays(), product.getStatus());
    }
}
