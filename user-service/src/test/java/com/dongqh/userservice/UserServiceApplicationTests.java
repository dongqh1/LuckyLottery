package com.dongqh.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.nacos.config.enabled=false")
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
