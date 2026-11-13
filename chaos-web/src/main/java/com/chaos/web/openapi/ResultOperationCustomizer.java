package com.chaos.web.openapi;

import com.chaos.web.advice.ResultResponseBodyAdvice;
import com.chaos.web.result.Result;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Locale;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Documents successful JSON responses wrapped by {@link ResultResponseBodyAdvice}.
 * Register as a bean in applications using the advice; applies to default and grouped documents.
 */
public final class ResultOperationCustomizer implements GlobalOperationCustomizer {

    private final ResultResponseBodyAdvice advice = new ResultResponseBodyAdvice();

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        if (operation.getResponses() == null || !advice.supports(handler.getReturnType(),
                MappingJackson2HttpMessageConverter.class)) {
            return operation;
        }
        ResolvableType type = ResolvableType.forMethodParameter(handler.getReturnType());
        Class<?> body = HttpEntity.class.isAssignableFrom(handler.getReturnType().getParameterType())
                ? type.as(HttpEntity.class).getGeneric(0).resolve(Object.class) : type.resolve(Object.class);
        if (body == byte[].class || Result.class.isAssignableFrom(body) || CharSequence.class.isAssignableFrom(body)
                || Resource.class.isAssignableFrom(body) || StreamingResponseBody.class.isAssignableFrom(body)) {
            return operation;
        }
        operation.getResponses().forEach((status, response) -> {
            if (!status.startsWith("2") || response == null || response.getContent() == null) {
                return;
            }
            response.getContent().forEach((media, content) -> {
                String mime = media.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (!("application/json".equals(mime) || mime.endsWith("+json") || "*/*".equals(mime))) {
                    return;
                }
                Schema<?> data = content.getSchema();
                if (data != null && !(data instanceof ResultSchema)
                        && !"#/components/schemas/Result".equals(data.get$ref())
                        && !"binary".equals(data.getFormat())) {
                    content.setSchema(new ResultSchema(data));
                }
            });
        });
        return operation;
    }

    private static final class ResultSchema extends ObjectSchema {
        private ResultSchema(Schema<?> data) {
            addProperty("code", new StringSchema().description("业务码，0 表示成功").example("0"));
            addProperty("message", new StringSchema().description("提示信息").example("success"));
            addProperty("data", data);
            addProperty("traceId", new StringSchema().description("链路追踪标识"));
            addProperty("timestamp", new IntegerSchema().format("int64").description("服务器时间戳，单位毫秒"));
        }
    }
}
