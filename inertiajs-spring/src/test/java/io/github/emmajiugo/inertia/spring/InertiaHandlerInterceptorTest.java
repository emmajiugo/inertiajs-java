package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaConfig;
import io.github.emmajiugo.inertia.core.InertiaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InertiaHandlerInterceptorTest {

    private InertiaHandlerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        InertiaConfig config = InertiaConfig.builder()
                .version("1.0.0")
                .templateResolver(json -> "<html>" + json + "</html>")
                .build();
        InertiaEngine engine = new InertiaEngine(config);
        interceptor = new InertiaHandlerInterceptor(engine);
    }

    @Test
    void setsVaryHeaderOnEveryRequest() {
        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        interceptor.preHandle(req, res, null);

        assertThat(res.getHeader("Vary")).isEqualTo("X-Inertia");
    }

    @Test
    void returns409OnVersionMismatch() {
        var req = new MockHttpServletRequest("GET", "/events");
        req.addHeader("X-Inertia", "true");
        req.addHeader("X-Inertia-Version", "old-version");
        var res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, null);

        assertThat(proceed).isFalse();
        assertThat(res.getStatus()).isEqualTo(409);
        assertThat(res.getHeader("X-Inertia-Location")).isNotNull();
    }

    @Test
    void allowsRequestWhenVersionsMatch() {
        var req = new MockHttpServletRequest("GET", "/events");
        req.addHeader("X-Inertia", "true");
        req.addHeader("X-Inertia-Version", "1.0.0");
        var res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, null);

        assertThat(proceed).isTrue();
    }

    @Test
    void allowsNonInertiaRequests() {
        var req = new MockHttpServletRequest("GET", "/events");
        var res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, null);

        assertThat(proceed).isTrue();
    }

    @Test
    void upgradesRedirect302To303ForPut() {
        var req = new MockHttpServletRequest("PUT", "/events/1");
        req.addHeader("X-Inertia", "true");
        var res = new MockHttpServletResponse();
        res.setStatus(302);

        interceptor.postHandle(req, res, null, null);

        assertThat(res.getStatus()).isEqualTo(303);
    }

    @Test
    void doesNotUpgradeRedirectForGet() {
        var req = new MockHttpServletRequest("GET", "/events");
        req.addHeader("X-Inertia", "true");
        var res = new MockHttpServletResponse();
        res.setStatus(302);

        interceptor.postHandle(req, res, null, null);

        assertThat(res.getStatus()).isEqualTo(302);
    }
}
