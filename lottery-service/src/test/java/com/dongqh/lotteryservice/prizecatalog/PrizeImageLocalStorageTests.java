package com.dongqh.lotteryservice.prizecatalog;

import com.dongqh.lotteryservice.config.prizecatalog.OssProperties;
import com.dongqh.lotteryservice.domain.image.prizecatalog.PrizeImageValidator;
import com.dongqh.lotteryservice.integration.prizecatalog.LocalFileSystemObjectStorageGateway;
import com.dongqh.lotteryservice.service.prizecatalog.PrizeImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrizeImageLocalStorageTests {
    @TempDir
    Path uploadDirectory;

    @Test
    void storesImageLocallyAndReturnsPublicUploadsPathWhenOssIsNotConfigured() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        var storage = new LocalFileSystemObjectStorageGateway(uploadDirectory);
        var service = new PrizeImageService(new PrizeImageValidator(), storage,
                new OssProperties(false, "", "", "", "", "", ""));

        var result = service.upload(new MockMultipartFile("file", "gift.png", "image/png", png));

        assertThat(result.url()).startsWith("/uploads/prizes/");
        assertThat(Files.readAllBytes(uploadDirectory.resolve(result.objectKey()))).containsExactly(png);
    }

    @Test
    void localStorageRejectsKeysThatEscapeItsUploadDirectory() {
        var storage = new LocalFileSystemObjectStorageGateway(uploadDirectory);

        assertThatThrownBy(() -> storage.put("../outside.png", new byte[]{1}, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
