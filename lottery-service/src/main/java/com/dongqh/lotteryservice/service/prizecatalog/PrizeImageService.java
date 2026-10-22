package com.dongqh.lotteryservice.service.prizecatalog;

import com.dongqh.lotteryservice.config.prizecatalog.OssProperties;
import com.dongqh.lotteryservice.domain.image.prizecatalog.PrizeImageValidator;
import com.dongqh.lotteryservice.domain.image.prizecatalog.ValidatedImage;
import com.dongqh.lotteryservice.integration.prizecatalog.ObjectStorageGateway;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.ImageUploadView;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PrizeImageService {

    private static final DateTimeFormatter PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final PrizeImageValidator validator;
    private final ObjectStorageGateway gateway;
    private final OssProperties properties;

    public PrizeImageService(PrizeImageValidator validator, ObjectStorageGateway gateway,
                             OssProperties properties) {
        this.validator = validator;
        this.gateway = gateway;
        this.properties = properties;
    }

    public ImageUploadView upload(MultipartFile file) {
        ValidatedImage image = validator.validate(file);
        String objectKey = "prizes/%s/%s.%s".formatted(
                LocalDate.now(ZoneOffset.UTC).format(PATH_DATE), UUID.randomUUID(), image.extension());
        gateway.put(objectKey, image.content(), image.contentType());
        String url = properties.enabled() && properties.isComplete()
                ? properties.normalizedPublicBaseUrl() + "/" + objectKey
                : "/uploads/" + objectKey;
        return new ImageUploadView(url, objectKey);
    }
}
