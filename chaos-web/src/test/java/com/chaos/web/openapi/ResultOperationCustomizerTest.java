package com.chaos.web.openapi;

import com.chaos.web.annotation.IgnoreResponseWrap;
import com.chaos.web.result.Result;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import static org.assertj.core.api.Assertions.assertThat;

class ResultOperationCustomizerTest {
    private final ResultOperationCustomizer customizer = new ResultOperationCustomizer();
    private final StringSchema original = new StringSchema();

    @Test
    void wrapsJsonOnceAndPreservesTheDeclaredPayloadSchema() throws Exception {
        var operation = operation("application/vnd.acme+json", "200");
        var handler = handler("json");
        customizer.customize(operation, handler);
        customizer.customize(operation, handler);
        var schema = operation.getResponses().get("200").getContent().values().iterator().next().getSchema();
        assertThat(schema.getProperties()).containsKeys("code", "message", "data", "traceId", "timestamp");
        assertThat(schema.getProperties().get("data")).isSameAs(original);
        assertThat(original.getDescription()).isNull();
    }

    @Test
    void skipsRawExplicitResultFilesEventsAndErrors() throws Exception {
        for (var name : new String[]{"raw", "result", "file", "text"}) {
            assertUnchanged(name, "application/json", "200");
        }
        assertUnchanged("json", "text/event-stream", "200");
        assertUnchanged("json", "application/octet-stream", "200");
        assertUnchanged("json", "application/problem+json", "400");
    }

    private void assertUnchanged(String method, String media, String status) throws Exception {
        var operation = operation(media, status);
        customizer.customize(operation, handler(method));
        assertThat(operation.getResponses().get(status).getContent().get(media).getSchema()).isSameAs(original);
    }

    private Operation operation(String media, String status) {
        return new Operation().responses(new ApiResponses().addApiResponse(status, new ApiResponse().content(
                new Content().addMediaType(media, new MediaType().schema(original)))));
    }

    private HandlerMethod handler(String name) throws Exception {
        return new HandlerMethod(this, getClass().getDeclaredMethod(name));
    }

    Object json() { return null; }
    @IgnoreResponseWrap Object raw() { return null; }
    ResponseEntity<Result<Object>> result() { return null; }
    ResponseEntity<byte[]> file() { return null; }
    ResponseEntity<String> text() { return null; }
}
