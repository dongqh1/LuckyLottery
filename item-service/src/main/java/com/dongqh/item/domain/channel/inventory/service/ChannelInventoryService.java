package com.dongqh.item.domain.channel.inventory.service;

import com.dongqh.item.domain.channel.inventory.dto.AllocateChannelStockCommand;
import com.dongqh.item.domain.channel.inventory.dto.InitializeSkuStockCommand;
import com.dongqh.item.domain.channel.inventory.dto.ReserveChannelStockCommand;
import com.dongqh.item.domain.channel.inventory.vo.ChannelInventoryView;

public interface ChannelInventoryService {
    ChannelInventoryView initialize(InitializeSkuStockCommand command);
    ChannelInventoryView allocate(AllocateChannelStockCommand command);
    ChannelInventoryView reserve(ReserveChannelStockCommand command);
    ChannelInventoryView confirm(String reservationNo);
    ChannelInventoryView release(String reservationNo);
    ChannelInventoryView reverseConfirmed(String reservationNo);
    ChannelInventoryView getReservation(String reservationNo);
    ChannelInventoryView get(long skuId, String channelCode);
}
