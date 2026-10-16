package com.dongqh.luckyhub.config.prize;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.dongqh.luckyhub.domain.storage.prize.AliyunOssObjectStorageGateway;
import com.dongqh.luckyhub.domain.storage.prize.ObjectStorageGateway;
import com.dongqh.luckyhub.domain.storage.prize.UnavailableObjectStorageGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    @Bean
    public ObjectStorageGateway objectStorageGateway(OssProperties properties) {
        if (!properties.enabled() || !properties.isComplete()) {
            return new UnavailableObjectStorageGateway();
        }
        OSSClient client = OSSClient.newBuilder()
                .region(properties.region())
                .endpoint(properties.endpoint())
                .credentialsProvider(new StaticCredentialsProvider(
                        properties.accessKeyId(),
                        properties.accessKeySecret()
                ))
                .build();
        return new AliyunOssObjectStorageGateway(client, properties.bucket());
    }
}
