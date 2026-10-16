package com.dongqh.lotteryservice.integration.prizecatalog;

import com.dongqh.lotteryservice.exception.prizecatalog.PrizeCatalogException;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeErrorCode;

public final class UnavailableObjectStorageGateway implements ObjectStorageGateway {

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        throw new PrizeCatalogException(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE);
    }
}
