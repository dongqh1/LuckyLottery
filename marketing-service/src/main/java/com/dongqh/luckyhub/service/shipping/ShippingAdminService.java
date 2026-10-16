package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.shipping.ShippingOrderQuery;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingOrderView;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingTrackingView;

import java.util.List;

public interface ShippingAdminService {
    PageResponse<ShippingOrderView> page(ShippingOrderQuery query);
    ShippingOrderView get(String shippingNo);
    List<ShippingTrackingView.TrackingEvent> tracking(String shippingNo);
    ShippingOrderView retry(String shippingNo, long operatorId, String note);
    ShippingOrderView terminate(String shippingNo, long operatorId, String note);
    int projectPending();
}
