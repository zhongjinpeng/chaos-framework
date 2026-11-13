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

    @Test
    void shouldCanonicalizeObjectWithoutExplicitTreeConversion() {
        String result = jsonUtils.toCanonicalJson(Map.of("z", 1, "a", Map.of("y", 2, "x", 3)));

        assertThat(result).isEqualTo("{\"a\":{\"x\":3,\"y\":2},\"z\":1}");
    }

    @Test
    void shouldRejectNonObjectWhenCanonicalObjectRequired() {
        assertThatThrownBy(() -> jsonUtils.toCanonicalObject(List.of(1, 2)))
                .isInstanceOf(JsonOperationException.class)
                .hasMessage("JSON value must be an object");
    }

    @Test
    void shouldCanonicalizeObjectWithSortedFields() {
        assertThat(jsonUtils.toCanonicalObject(Map.of("b", 1, "a", 2)))
                .isEqualTo("{\"a\":2,\"b\":1}");
    }
}
