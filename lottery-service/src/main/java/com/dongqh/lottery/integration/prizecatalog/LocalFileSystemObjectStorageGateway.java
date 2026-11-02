package com.dongqh.lottery.integration.prizecatalog;

import com.dongqh.lottery.enums.prizecatalog.PrizeErrorCode;
import com.dongqh.lottery.exception.prizecatalog.PrizeCatalogException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class LocalFileSystemObjectStorageGateway implements ObjectStorageGateway {
    private final Path root;

    public LocalFileSystemObjectStorageGateway(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("image object key escapes local storage root");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new PrizeCatalogException(PrizeErrorCode.OSS_UPLOAD_FAILED);
        }
    }
}
