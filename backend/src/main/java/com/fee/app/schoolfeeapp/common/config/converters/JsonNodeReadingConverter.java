package com.fee.app.schoolfeeapp.common.config.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.common.exceptions.JsonConversionException;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

@Component
@ReadingConverter
public class JsonNodeReadingConverter implements Converter<Json, JsonNode> {

    private final ObjectMapper objectMapper;

    public JsonNodeReadingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode convert(Json source) {
        try {
            return objectMapper.readTree(source.asString());
        } catch (JsonProcessingException e) {
      throw new JsonConversionException("Error converting Json to JsonNode", e);
        }
    }
}
