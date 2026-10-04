package com.dongqh.lotteryservice.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.lotteryservice.prizecatalog.service.PrizeImageService;
import com.dongqh.lotteryservice.prizecatalog.vo.ImageUploadView;
import com.hmall.common.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/prize-images")
public class PrizeImageController {

    private final PrizeImageService images;

    public PrizeImageController(PrizeImageService images) {
        this.images = images;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.PRIZE_IMAGE_UPLOAD)
    public R<ImageUploadView> upload(@RequestPart("file") MultipartFile file) {
        return R.ok(images.upload(file));
    }
}
