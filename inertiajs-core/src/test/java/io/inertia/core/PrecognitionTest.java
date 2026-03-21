package io.inertia.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrecognitionTest {

    private final JsonSerializer serializer = new JacksonJsonSerializer();

    @Test
    void detectsPrecognitionRequest() {
        var req = new StubInertiaRequest().withHeader("Precognition", "true");
        assertThat(Precognition.isPrecognitionRequest(req)).isTrue();
    }

    @Test
    void nonPrecognitionRequest() {
        var req = new StubInertiaRequest();
        assertThat(Precognition.isPrecognitionRequest(req)).isFalse();
    }

    @Test
    void parsesValidateOnlyFields() {
        var req = new StubInertiaRequest()
                .withHeader("Precognition", "true")
                .withHeader("Precognition-Validate-Only", "title,email");
        String[] fields = Precognition.getValidateOnly(req);
        assertThat(fields).containsExactly("title", "email");
    }

    @Test
    void returns204WhenNoErrors() throws IOException {
        var res = new StubInertiaResponse();
        Precognition.respond(res, Map.of(), serializer);

        assertThat(res.getStatus()).isEqualTo(204);
        assertThat(res.getHeader("Precognition")).isEqualTo("true");
        assertThat(res.getHeader("Precognition-Success")).isEqualTo("true");
        assertThat(res.getHeader("Vary")).isEqualTo("Precognition");
    }

    @Test
    void returns422WithErrors() throws IOException {
        var res = new StubInertiaResponse();
        Precognition.respond(res, Map.of("title", "Title is required"), serializer);

        assertThat(res.getStatus()).isEqualTo(422);
        assertThat(res.getHeader("Precognition")).isEqualTo("true");
        assertThat(res.getHeader("Vary")).isEqualTo("Precognition");
        assertThat(res.getContentType()).isEqualTo("application/json");
        assertThat(res.getBody()).contains("Title is required");
    }

    @Test
    void returns204WhenErrorsNull() throws IOException {
        var res = new StubInertiaResponse();
        Precognition.respond(res, null, serializer);

        assertThat(res.getStatus()).isEqualTo(204);
        assertThat(res.getHeader("Precognition-Success")).isEqualTo("true");
    }
}
