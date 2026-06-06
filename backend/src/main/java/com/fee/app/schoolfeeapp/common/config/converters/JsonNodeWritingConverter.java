package com.fee.app.schoolfeeapp.common.config.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.common.exceptions.JsonConversionException;
import io.r2dbc.postgresql.codec.Json;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class JsonNodeWritingConverter implements Converter<JsonNode, Json> {

    private final ObjectMapper objectMapper;

    public JsonNodeWritingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Json convert(@NonNull JsonNode source) {
        try {
            return Json.of(objectMapper.writeValueAsString(source));
        } catch (JsonProcessingException e) {
            throw new JsonConversionException("Error converting JsonNode to Json", e);
        }
    }
}
