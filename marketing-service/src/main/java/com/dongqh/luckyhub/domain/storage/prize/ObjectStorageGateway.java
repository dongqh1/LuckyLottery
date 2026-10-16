package com.dongqh.luckyhub.domain.storage.prize;

public interface ObjectStorageGateway extends AutoCloseable {

    void put(String objectKey, byte[] content, String contentType);

    @Override
    default void close() throws Exception {
    }
}
