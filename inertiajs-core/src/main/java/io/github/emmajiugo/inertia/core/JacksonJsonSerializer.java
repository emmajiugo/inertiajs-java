package io.github.emmajiugo.inertia.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;

public final class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper mapper;

    public JacksonJsonSerializer() {
        this(JsonMapper.builder().findAndAddModules().build());
    }

    public JacksonJsonSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }
}
