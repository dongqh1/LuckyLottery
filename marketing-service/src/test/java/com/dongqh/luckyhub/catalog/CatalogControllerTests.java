package com.dongqh.luckyhub.catalog;

import com.dongqh.luckyhub.domain.model.auth.JwtPayload;
import com.dongqh.luckyhub.security.auth.JwtService;
import com.dongqh.luckyhub.security.auth.SessionService;
import com.dongqh.luckyhub.controller.catalog.CatalogAdminController;
import com.dongqh.luckyhub.controller.catalog.CatalogController;
import com.dongqh.luckyhub.enums.catalog.ProductType;
import com.dongqh.luckyhub.service.catalog.CatalogService;
import com.dongqh.luckyhub.domain.vo.catalog.ProductView;
import com.dongqh.luckyhub.domain.vo.catalog.SkuView;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.annotation.rbac.RequirePermission;
import com.dongqh.luckyhub.domain.constant.rbac.PermissionCodes;
import com.dongqh.luckyhub.service.rbac.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private UserPermissionService userPermissionService;

    @BeforeEach
    void prepareCaller() {
        when(jwtService.parse("valid-token")).thenReturn(new JwtPayload(7711L, "catalog-user", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
    }

    @Test
    void createsProductWithManagePermission() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(PermissionCodes.CATALOG_MANAGE));
        when(service.create(any())).thenReturn(view());

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    void readsProductsWithReadPermissionWithoutInventoryFields() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(PermissionCodes.CATALOG_READ));
        when(service.page(any())).thenReturn(new PageResponse<>(List.of(view()), 1, 1, 20, 1));
        when(service.get(7L)).thenReturn(view());

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].skus[0].cashPriceCent").value(1999))
                .andExpect(jsonPath("$.data.records[0].skus[0].pointsEnabled").value(true))
                .andExpect(jsonPath("$.data.records[0].skus[0].availableStock").doesNotExist());
        mockMvc.perform(get("/api/products/7")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("PROD-1"));
    }

    @Test
    void rejectsInvalidCommandWithValidationEnvelope() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(PermissionCodes.CATALOG_MANAGE));
        String invalidBody = validBody().replace("测试商品", "");

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    @Test
    void realFilterAndPermissionInterceptorReturn401And403() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of());

        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignsExactPermissions() throws NoSuchMethodException {
        Method create = CatalogAdminController.class.getMethod(
                "create", com.dongqh.luckyhub.domain.dto.catalog.CreateProductCommand.class);
        Method page = CatalogController.class.getMethod(
                "page", com.dongqh.luckyhub.domain.dto.catalog.ProductQuery.class);
        Method getById = CatalogController.class.getMethod("get", long.class);

        assertThat(create.getAnnotation(RequirePermission.class).value()).isEqualTo(PermissionCodes.CATALOG_MANAGE);
        assertThat(page.getAnnotation(RequirePermission.class).value()).isEqualTo(PermissionCodes.CATALOG_READ);
        assertThat(getById.getAnnotation(RequirePermission.class).value()).isEqualTo(PermissionCodes.CATALOG_READ);
    }

    private ProductView view() {
        SkuView sku = new SkuView(8L, 7L, "SKU-1", "默认SKU", 1999L, 2000L,
                true, true, 1, 0, null, null);
        return new ProductView(7L, "PROD-1", "测试商品", ProductType.PHYSICAL,
                "https://cdn.example/product.png", "商品说明", 1, List.of(sku), null, null);
    }

    private String validBody() {
        return """
                {
                  "productCode": "PROD-1",
                  "productName": "测试商品",
                  "productType": "PHYSICAL",
                  "skuCode": "SKU-1",
                  "skuName": "默认SKU",
                  "cashPriceCent": 1999,
                  "pointsPrice": 2000,
                  "cashEnabled": true,
                  "pointsEnabled": true
                }
                """;
    }
}
