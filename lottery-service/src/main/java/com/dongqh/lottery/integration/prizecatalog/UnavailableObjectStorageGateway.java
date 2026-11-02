package com.dongqh.lottery.integration.prizecatalog;

import com.dongqh.lottery.exception.prizecatalog.PrizeCatalogException;
import com.dongqh.lottery.enums.prizecatalog.PrizeErrorCode;

public final class UnavailableObjectStorageGateway implements ObjectStorageGateway {

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        throw new PrizeCatalogException(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE);
    }
}
