package com.hmall.common.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GroupedOpenApi.class)
@EnableConfigurationProperties(SwaggerProperties.class)
public class OpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI hmallOpenApi(SwaggerProperties properties) {
        return new OpenAPI().info(new Info()
                .title(properties.getTitle())
                .description(properties.getDescription()));
    }

    @Bean
    @ConditionalOnMissingBean(GroupedOpenApi.class)
    public GroupedOpenApi hmallGroupedOpenApi(SwaggerProperties properties) {
        return GroupedOpenApi.builder()
                .group("default")
                .packagesToScan(properties.getPackage())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "legacyDocRedirectConfigurer")
    public WebMvcConfigurer legacyDocRedirectConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController("/doc.html", "/swagger-ui.html");
            }
        };
    }
}
