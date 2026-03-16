package io.inertia.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper mapper;

    public JacksonJsonSerializer() {
        this(new ObjectMapper());
    }

    public JacksonJsonSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }
}
