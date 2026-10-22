package com.dongqh.lotteryservice.config.prizecatalog;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.dongqh.lotteryservice.integration.prizecatalog.AliyunOssObjectStorageGateway;
import com.dongqh.lotteryservice.integration.prizecatalog.LocalFileSystemObjectStorageGateway;
import com.dongqh.lotteryservice.integration.prizecatalog.ObjectStorageGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    @Bean
    public ObjectStorageGateway objectStorageGateway(OssProperties properties,
            @Value("${luckyhub.oss.local-directory:/app/uploads}") String localDirectory) {
        if (!properties.enabled() || !properties.isComplete()) {
            return new LocalFileSystemObjectStorageGateway(Path.of(localDirectory));
        }
        OSSClient client = OSSClient.newBuilder()
                .region(properties.region())
                .endpoint(properties.endpoint())
                .credentialsProvider(new StaticCredentialsProvider(
                        properties.accessKeyId(), properties.accessKeySecret()))
                .build();
        return new AliyunOssObjectStorageGateway(client, properties.bucket());
    }
}
