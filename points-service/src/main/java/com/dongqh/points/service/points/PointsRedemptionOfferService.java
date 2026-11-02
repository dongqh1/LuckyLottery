package com.dongqh.points.service.points;

import com.dongqh.points.domain.dto.points.CreatePointsRedemptionOfferCommand;
import com.dongqh.points.domain.po.points.PointsRedemptionOffer;
import com.dongqh.points.domain.vo.points.PointsRedemptionOfferView;

import java.util.List;

public interface PointsRedemptionOfferService {
    PointsRedemptionOfferView create(CreatePointsRedemptionOfferCommand command);
    List<PointsRedemptionOfferView> listAvailable();
    PointsRedemptionOffer requireAvailable(long offerId);
    void deductStock(long offerId, int quantity);
    void restoreStock(long offerId, int quantity);
}
