package com.hmall.service.impl;

import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.mapper.ItemMapper;
import com.hmall.utils.JwtTool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemServiceImplTest {

    @Test
    void testJwt() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        JwtTool jwtTool = new JwtTool(keyPair);

        String token = jwtTool.createToken(1L, Duration.ofMinutes(30));

        assertThat(token).isNotBlank();
    }

    @Test
    void deductStock() {
        ItemMapper mapper = mock(ItemMapper.class);
        ItemServiceImpl itemService = new ItemServiceImpl();
        ReflectionTestUtils.setField(itemService, "baseMapper", mapper);
        List<OrderDetailDTO> items = List.of(
                new OrderDetailDTO().setItemId(317578L).setNum(1),
                new OrderDetailDTO().setItemId(317580L).setNum(1)
        );
        when(mapper.updateStock(items.get(0))).thenReturn(1);
        when(mapper.updateStock(items.get(1))).thenReturn(1);

        assertThatCode(() -> itemService.deductStock(items)).doesNotThrowAnyException();
    }
}
