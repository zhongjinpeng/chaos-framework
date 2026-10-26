package com.chaos.service.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

    private final JsonUtils jsonUtils = new JsonUtils(new ObjectMapper());

    @Test
    void shouldSerializeAndDeserializeGenericValue() {
        String json = jsonUtils.toJson(Map.of("items", List.of(1, 2)));

        Map<String, List<Integer>> result = jsonUtils.fromJson(json, new TypeReference<>() {});

        assertThat(result).containsEntry("items", List.of(1, 2));
    }

    @Test
    void shouldProduceCanonicalJsonWithoutReorderingArrays() {
        String result = jsonUtils.toCanonicalJson(jsonUtils.readTree("{\"z\":1,\"a\":{\"y\":2,\"x\":3},\"b\":[2,1]}"));

        assertThat(result).isEqualTo("{\"a\":{\"x\":3,\"y\":2},\"b\":[2,1],\"z\":1}");
    }

    @Test
    void shouldWrapInvalidJson() {
        assertThatThrownBy(() -> jsonUtils.readTree("{"))
                .isInstanceOf(JsonOperationException.class)
                .hasMessage("JSON parsing failed");
    }
}
