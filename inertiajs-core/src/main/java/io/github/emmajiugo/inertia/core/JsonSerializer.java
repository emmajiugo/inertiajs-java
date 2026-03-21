package io.github.emmajiugo.inertia.core;

import java.io.IOException;

public interface JsonSerializer {

    String serialize(Object value) throws IOException;
}
