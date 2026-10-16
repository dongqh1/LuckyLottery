package com.dongqh.luckyhub.domain.storage.prize;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.enums.prize.PrizeErrorCode;

public final class UnavailableObjectStorageGateway implements ObjectStorageGateway {

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        throw new BusinessException(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE);
    }
}
