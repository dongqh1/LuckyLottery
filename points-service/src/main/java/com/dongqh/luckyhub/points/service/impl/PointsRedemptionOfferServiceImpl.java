package com.dongqh.luckyhub.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOffer;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.exception.PointsDomainException;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOfferMapper;
import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.service.port.PointsItemCatalogPort;
import com.dongqh.luckyhub.points.service.port.PointsItemSnapshot;
import com.dongqh.luckyhub.points.vo.PointsRedemptionOfferView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointsRedemptionOfferServiceImpl implements PointsRedemptionOfferService {
    private static final int ENABLED = 1;

    private final PointsItemCatalogPort itemCatalog;
    private final PointsRedemptionOfferMapper mapper;

    public PointsRedemptionOfferServiceImpl(
            PointsItemCatalogPort itemCatalog,
            PointsRedemptionOfferMapper mapper
    ) {
        this.itemCatalog = itemCatalog;
        this.mapper = mapper;
    }

    @Override
    public PointsRedemptionOfferView create(CreatePointsRedemptionOfferCommand command) {
        PointsItemSnapshot item = itemCatalog.getItem(command.hmallItemId())
                .filter(snapshot -> snapshot.id() != null && snapshot.id().equals(command.hmallItemId()))
                .filter(snapshot -> Integer.valueOf(ENABLED).equals(snapshot.status()))
                .filter(snapshot -> snapshot.stock() != null
                        && snapshot.stock() >= command.availableStock())
                .orElseThrow(() -> error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE));
        PointsRedemptionOffer offer = new PointsRedemptionOffer();
        offer.setHmallItemId(item.id());
        offer.setUnitPoints(command.unitPoints());
        offer.setAvailableStock(command.availableStock());
        offer.setItemName(item.name());
        offer.setImageUrl(item.imageUrl());
        offer.setCashPriceCent(item.priceCent());
        offer.setStatus(ENABLED);
        mapper.insert(offer);
        return view(offer);
    }

    @Override
    public List<PointsRedemptionOfferView> listAvailable() {
        return mapper.selectList(new LambdaQueryWrapper<PointsRedemptionOffer>()
                        .eq(PointsRedemptionOffer::getStatus, ENABLED)
                        .gt(PointsRedemptionOffer::getAvailableStock, 0)
                        .orderByAsc(PointsRedemptionOffer::getId))
                .stream().map(this::view).toList();
    }

    @Override
    public PointsRedemptionOffer requireAvailable(long offerId) {
        PointsRedemptionOffer offer = mapper.selectById(offerId);
        if (offer == null || !Integer.valueOf(ENABLED).equals(offer.getStatus())
                || offer.getAvailableStock() == null || offer.getAvailableStock() <= 0) {
            throw error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE);
        }
        return offer;
    }

    @Override
    public void deductStock(long offerId, int quantity) {
        if (mapper.deductStockIfAvailable(offerId, quantity) != 1) {
            throw error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE);
        }
    }

    @Override
    public void restoreStock(long offerId, int quantity) {
        if (mapper.restoreStockIfNoOverflow(offerId, quantity) != 1) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
    }

    private PointsRedemptionOfferView view(PointsRedemptionOffer offer) {
        return new PointsRedemptionOfferView(offer.getId(), offer.getHmallItemId(),
                offer.getUnitPoints(), offer.getAvailableStock(), offer.getItemName(),
                offer.getImageUrl(), offer.getCashPriceCent());
    }

    private PointsDomainException error(PointsErrorCode errorCode) {
        return new PointsDomainException(errorCode);
    }
}
