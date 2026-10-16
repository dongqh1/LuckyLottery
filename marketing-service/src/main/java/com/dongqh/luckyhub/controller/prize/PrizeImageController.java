package com.dongqh.luckyhub.controller.prize;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.image.prize.PrizeImageService;
import com.dongqh.luckyhub.domain.vo.prize.ImageUploadView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "奖品图片", description = "奖品图片上传接口")
public class PrizeImageController {

    private final PrizeImageService service;

    public PrizeImageController(PrizeImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传奖品图片")
    public ApiResponse<ImageUploadView> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(file));
    }
}

