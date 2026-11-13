package com.chaos.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeLongSerializerTest {
    @Test
    void roundTripsUnsafeIdsWithoutChangingSafeCountsOrDecimals() throws Exception {
        var module = new SimpleModule().addSerializer(Long.class, new SafeLongSerializer())
                .addSerializer(Long.TYPE, new SafeLongSerializer());
        var mapper = new ObjectMapper().registerModule(module);
        var json = mapper.readTree(mapper.writeValueAsString(Map.of(
                "id", 9007199254740993L, "count", 12L, "amount", new BigDecimal("12.50"),
                "min", Long.MIN_VALUE, "max", Long.MAX_VALUE, "boundary", 9007199254740991L)));
        assertThat(json.get("id").textValue()).isEqualTo("9007199254740993");
        assertThat(json.get("min").textValue()).isEqualTo(Long.toString(Long.MIN_VALUE));
        assertThat(json.get("max").textValue()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(json.get("count").isNumber()).isTrue();
        assertThat(json.get("amount").isNumber()).isTrue();
        assertThat(json.get("boundary").isNumber()).isTrue();
        assertThat(mapper.readValue(json.get("id").toString(), Long.class)).isEqualTo(9007199254740993L);
    }
}
