package com.hmall.common.swagger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hm.swagger")
public class SwaggerProperties {

    private String title;
    private String description;
    private String controllerPackage;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPackage() {
        return controllerPackage;
    }

    public void setPackage(String controllerPackage) {
        this.controllerPackage = controllerPackage;
    }
}
