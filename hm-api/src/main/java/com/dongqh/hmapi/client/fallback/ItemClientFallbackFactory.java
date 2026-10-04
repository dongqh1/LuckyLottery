package com.dongqh.hmapi.client.fallback;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.ItemStockReservationDTO;
import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.hmapi.dto.RedeemableSkuDTO;
import com.dongqh.hmapi.dto.ReservePointsStockRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.annotation.Bean;

import java.util.Collection;
import java.util.Collections;
import java.util.List;


@Slf4j
public class ItemClientFallbackFactory implements FallbackFactory<ItemClient> {
    @Override
    public ItemClient create(Throwable cause) {
        return new ItemClient() {
            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                log.error("查询商品失败",cause);
                return Collections.emptyList();
            }

            @Override
            public void deductStock(List<OrderDetailDTO> items) {
                log.error("扣减商品失败",cause);
                throw unavailable("扣减商品失败");
            }

            @Override
            public RedeemableSkuDTO getRedeemableSku(long skuId) {
                log.error("查询积分兑换SKU失败", cause);
                return null;
            }

            @Override
            public ItemStockReservationDTO reservePointsStock(ReservePointsStockRequest request) {
                throw unavailable("预占积分兑换库存失败");
            }

            @Override
            public ItemStockReservationDTO confirmPointsStock(String reservationNo) {
                throw unavailable("确认积分兑换库存失败");
            }

            @Override
            public ItemStockReservationDTO releasePointsStock(String reservationNo) {
                throw unavailable("释放积分兑换库存失败");
            }

            @Override
            public ItemStockReservationDTO reversePointsStock(String reservationNo) {
                throw unavailable("返还积分兑换库存失败");
            }

            @Override
            public ItemStockReservationDTO getPointsStockReservation(String reservationNo) {
                throw unavailable("查询积分兑换库存状态失败");
            }

            private RuntimeException unavailable(String action) {
                log.error(action, cause);
                return cause instanceof RuntimeException runtimeException
                        ? runtimeException : new RuntimeException(action, cause);
            }
        };
    }
}
