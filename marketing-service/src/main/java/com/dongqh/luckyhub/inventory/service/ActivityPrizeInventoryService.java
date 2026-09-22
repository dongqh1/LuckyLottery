package com.dongqh.luckyhub.inventory.service;

public interface ActivityPrizeInventoryService {

    boolean decrementIfAvailable(long activityPrizeId);

    void returnExpiredClaim(long activityPrizeId, long hmallItemId, String businessNo);
}
