package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOffer;
import com.dongqh.luckyhub.points.vo.PointsRedemptionOfferView;

import java.util.List;

public interface PointsRedemptionOfferService {
    PointsRedemptionOfferView create(CreatePointsRedemptionOfferCommand command);
    List<PointsRedemptionOfferView> listAvailable();
    PointsRedemptionOffer requireAvailable(long offerId);
    void deductStock(long offerId, int quantity);
    void restoreStock(long offerId, int quantity);
}
