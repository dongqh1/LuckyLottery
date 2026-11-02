package com.dongqh.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dongqh.hmapi.dto.TradeOrderShippingDetailsDTO;
import com.dongqh.hmapi.dto.UpdateTradeOrderShippingInfoRequest;
import com.dongqh.trade.domain.po.Order;
import com.dongqh.trade.domain.po.OrderDetail;
import com.dongqh.trade.domain.po.OrderLogistics;
import com.dongqh.trade.mapper.OrderMapper;
import com.hmall.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InternalTradeShippingDetailsService {
    private final OrderMapper orders;
    private final IOrderLogisticsService logistics;
    private final IOrderDetailService details;

    public InternalTradeShippingDetailsService(OrderMapper orders, IOrderLogisticsService logistics,
                                               IOrderDetailService details) {
        this.orders = orders;
        this.logistics = logistics;
        this.details = details;
    }

    public TradeOrderShippingDetailsDTO getPaidOrder(long orderId) {
        if (orderId <= 0) throw new BadRequestException("订单编号无效");
        Order order = orders.selectById(orderId);
        if (order == null) throw new BadRequestException("订单不存在");
        if (!Objects.equals(order.getStatus(), 2) || order.getPayTime() == null) {
            throw new BadRequestException("订单尚未付款");
        }
        OrderLogistics address = logistics.getById(orderId);
        if (address == null) throw new BadRequestException("订单收货地址快照不存在");

        List<OrderDetail> lines = details.list(new QueryWrapper<OrderDetail>().eq("order_id", orderId));
        if (lines == null || lines.isEmpty()) throw new BadRequestException("订单商品快照不存在");
        int quantity;
        try {
            quantity = lines.stream().map(OrderDetail::getNum).filter(Objects::nonNull)
                    .reduce(0, Math::addExact);
        } catch (ArithmeticException overflow) {
            throw new BadRequestException("订单商品数量无效");
        }
        if (quantity <= 0 || lines.stream().anyMatch(line -> line.getNum() == null || line.getNum() <= 0
                || line.getName() == null || line.getName().isBlank())) {
            throw new BadRequestException("订单商品快照无效");
        }
        String productName = lines.stream().map(line -> line.getName().trim() + " × " + line.getNum())
                .collect(Collectors.joining("、"));
        if (productName.length() > 100) productName = productName.substring(0, 99) + "…";
        String image = lines.stream().map(OrderDetail::getImage)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        return new TradeOrderShippingDetailsDTO(orderId, order.getUserId(), order.getPayTime(),
                address.getContact(), address.getMobile(), address.getProvince(), address.getCity(),
                address.getTown(), address.getStreet(), "TRADE-ORDER-" + orderId,
                productName, image, quantity);
    }

    @Transactional
    public void updateShippingInfo(long orderId, UpdateTradeOrderShippingInfoRequest request) {
        if (orderId <= 0 || request == null || request.carrierName() == null
                || request.carrierName().isBlank() || request.carrierName().trim().length() > 64
                || request.trackingNumber() == null || request.trackingNumber().isBlank()
                || request.trackingNumber().trim().length() > 100) {
            throw new BadRequestException("物流信息无效");
        }
        Order order = orders.selectByIdForUpdate(orderId);
        if (order == null || order.getStatus() == null || order.getStatus() < 2) {
            throw new BadRequestException("订单尚未付款");
        }
        OrderLogistics current = logistics.getById(orderId);
        if (current == null) throw new BadRequestException("订单收货地址快照不存在");
        String carrierName = request.carrierName().trim();
        String trackingNumber = request.trackingNumber().trim();
        if (carrierName.equals(current.getLogisticsCompany())
                && trackingNumber.equals(current.getLogisticsNumber())) {
            return;
        }
        current.setLogisticsCompany(carrierName).setLogisticsNumber(trackingNumber);
        if (!logistics.updateById(current)) throw new BadRequestException("物流信息更新失败");
    }
}
