package io.github.emmajiugo.inertia.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpSsrClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesSuccessfulSsrResponse() throws IOException {
        server.createContext("/render", exchange -> {
            String response = """
                {"head":["<title>Test</title>","<meta name=\\"desc\\" content=\\"hi\\">"],"body":"<div id=\\"app\\">rendered</div>"}""";
            byte[] bytes = response.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        var client = new HttpSsrClient("http://127.0.0.1:" + port, Duration.ofSeconds(5));
        SsrResponse result = client.render("{\"component\":\"Test\"}");

        assertThat(result.head()).containsExactly("<title>Test</title>", "<meta name=\"desc\" content=\"hi\">");
        assertThat(result.body()).isEqualTo("<div id=\"app\">rendered</div>");
    }

    @Test
    void throwsOnNon200Response() throws IOException {
        server.createContext("/render", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        var client = new HttpSsrClient("http://127.0.0.1:" + port, Duration.ofSeconds(5));

        assertThatThrownBy(() -> client.render("{\"component\":\"Test\"}"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("500");
    }

    @Test
    void throwsOnConnectionRefused() {
        // Use a port with no server running
        var client = new HttpSsrClient("http://127.0.0.1:" + port, Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.render("{\"component\":\"Test\"}"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void throwsOnTimeout() throws IOException {
        server.createContext("/render", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        var client = new HttpSsrClient("http://127.0.0.1:" + port, Duration.ofMillis(100));

        assertThatThrownBy(() -> client.render("{\"component\":\"Test\"}"))
                .isInstanceOf(IOException.class);
    }
}
