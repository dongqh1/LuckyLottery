package com.dongqh.lotteryservice.integration.prizecatalog;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.dongqh.lotteryservice.exception.prizecatalog.PrizeCatalogException;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeErrorCode;

public final class AliyunOssObjectStorageGateway implements ObjectStorageGateway {

    private final OSSClient client;
    private final String bucket;

    public AliyunOssObjectStorageGateway(OSSClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .body(BinaryData.fromBytes(content))
                .build();
        try {
            client.putObject(request);
        } catch (RuntimeException exception) {
            throw new PrizeCatalogException(PrizeErrorCode.OSS_UPLOAD_FAILED);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
