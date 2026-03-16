package io.inertia.core;

import java.io.IOException;

public interface JsonSerializer {

    String serialize(Object value) throws IOException;
}
