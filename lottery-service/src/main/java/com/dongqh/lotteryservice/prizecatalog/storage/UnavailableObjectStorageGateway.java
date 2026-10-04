package com.dongqh.lotteryservice.prizecatalog.storage;

import com.dongqh.lotteryservice.prizecatalog.domain.PrizeCatalogException;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeErrorCode;

public final class UnavailableObjectStorageGateway implements ObjectStorageGateway {

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        throw new PrizeCatalogException(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE);
    }
}
