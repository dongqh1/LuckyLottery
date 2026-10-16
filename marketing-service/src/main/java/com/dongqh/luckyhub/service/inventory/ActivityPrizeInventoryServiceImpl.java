package com.dongqh.luckyhub.service.inventory;

import com.dongqh.luckyhub.mapper.inventory.ActivityPrizeInventoryMapper;
import com.dongqh.luckyhub.domain.channel.inventory.entity.InventoryLedger;
import com.dongqh.luckyhub.domain.channel.inventory.enums.InventoryOperation;
import com.dongqh.luckyhub.domain.channel.inventory.mapper.InventoryLedgerMapper;
import org.springframework.stereotype.Service;

@Service
public class ActivityPrizeInventoryServiceImpl implements ActivityPrizeInventoryService {

    private final ActivityPrizeInventoryMapper inventoryMapper;
    private final InventoryLedgerMapper ledgers;

    public ActivityPrizeInventoryServiceImpl(ActivityPrizeInventoryMapper inventoryMapper,
                                             InventoryLedgerMapper ledgers) {
        this.inventoryMapper = inventoryMapper;
        this.ledgers = ledgers;
    }

    @Override
    public boolean decrementIfAvailable(long activityPrizeId) {
        return inventoryMapper.decrementIfAvailable(activityPrizeId) == 1;
    }

    @Override
    public void returnExpiredClaim(long activityPrizeId, long hmallItemId, String businessNo) {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setBusinessNo(businessNo);
        ledger.setHmallItemId(hmallItemId);
        ledger.setChannelCode("LOTTERY");
        ledger.setOperation(InventoryOperation.CLAIM_RETURN);
        ledger.setQuantity(1);
        if (ledgers.claim(ledger) == 0) return;
        if (inventoryMapper.incrementIfBelowTotal(activityPrizeId) != 1) {
            throw new IllegalStateException("奖品库存回补失败");
        }
    }
}
