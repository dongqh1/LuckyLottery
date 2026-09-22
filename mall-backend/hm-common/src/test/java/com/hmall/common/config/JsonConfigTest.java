package com.hmall.common.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonConfigTest {
    @Test
    void serializesLongAndBigIntegerAsStrings() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonConfig().jsonMapperBuilderCustomizer().customize(builder);
        JsonMapper mapper = builder.build();
        assertEquals("\"9007199254740993\"", mapper.writeValueAsString(9007199254740993L));
        assertEquals("\"9007199254740995\"", mapper.writeValueAsString(new BigInteger("9007199254740995")));
    }
}
