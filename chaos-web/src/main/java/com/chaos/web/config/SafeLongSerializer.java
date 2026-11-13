package com.chaos.web.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** 仅把超出 JavaScript 安全整数范围的 Long 输出为字符串，普通计数仍输出数字。 */
public final class SafeLongSerializer extends JsonSerializer<Long> {
    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (value > 9007199254740991L || value < -9007199254740991L) {
            generator.writeString(value.toString());
        } else {
            generator.writeNumber(value);
        }
    }
}
