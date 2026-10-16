package com.dongqh.luckyhub.controller.catalog;

import com.dongqh.luckyhub.domain.dto.catalog.CreateProductCommand;
import com.dongqh.luckyhub.service.catalog.CatalogService;
import com.dongqh.luckyhub.domain.vo.catalog.ProductView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/products")
public class CatalogAdminController {

    private final CatalogService service;

    public CatalogAdminController(CatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductView> create(@Valid @RequestBody CreateProductCommand command) {
        return ApiResponse.success(service.create(command));
    }
}

