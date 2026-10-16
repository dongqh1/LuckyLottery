package com.dongqh.luckyhub.security.auth;

import com.dongqh.luckyhub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationTokenExtractorTests {

    @Test
    void extractsBareToken() {
        assertThat(AuthorizationTokenExtractor.extract("header.payload.signature"))
                .isEqualTo("header.payload.signature");
    }

    @Test
    void extractsBearerToken() {
        assertThat(AuthorizationTokenExtractor.extract("Bearer header.payload.signature"))
                .isEqualTo("header.payload.signature");
    }

    @Test
    void rejectsMissingOrEmptyAuthorization() {
        assertThatThrownBy(() -> AuthorizationTokenExtractor.extract(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AuthorizationTokenExtractor.extract("Bearer   "))
                .isInstanceOf(BusinessException.class);
    }
}
