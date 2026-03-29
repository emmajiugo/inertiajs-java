package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaConfig;
import io.github.emmajiugo.inertia.core.InertiaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InertiaSpringTest {

    private Inertia inertia;

    @BeforeEach
    void setUp() {
        InertiaConfig config = InertiaConfig.builder()
                .version("1.0.0")
                .templateResolver(pageJson -> "<html>" + pageJson + "</html>")
                .build();
        inertia = new Inertia(new InertiaEngine(config));
    }

    @Nested
    class ErrorBagStorage {

        @SuppressWarnings("unchecked")
        @Test
        void storesErrorsUnderNamedBag() {
            var req = new MockHttpServletRequest();
            var res = new MockHttpServletResponse();

            inertia.redirectWithErrors(req, res, "/form",
                    Map.of("email", "Invalid"), "login");

            var session = req.getSession();
            var storedErrors = (Map<String, Map<String, Object>>) session.getAttribute(Inertia.ERRORS_SESSION_KEY);
            assertThat(storedErrors).containsKey("login");
            assertThat(storedErrors.get("login")).containsEntry("email", "Invalid");
        }

        @SuppressWarnings("unchecked")
        @Test
        void storesErrorsUnderDefaultBagWhenNoBagSpecified() {
            var req = new MockHttpServletRequest();
            var res = new MockHttpServletResponse();

            inertia.redirectWithErrors(req, res, "/form",
                    Map.of("name", "Required"));

            var session = req.getSession();
            var storedErrors = (Map<String, Map<String, Object>>) session.getAttribute(Inertia.ERRORS_SESSION_KEY);
            assertThat(storedErrors).containsKey("default");
            assertThat(storedErrors.get("default")).containsEntry("name", "Required");
        }
    }

    @Nested
    class FragmentRedirectSpring {

        @Test
        void redirectWithFragmentSets409AndHeader() {
            var res = new MockHttpServletResponse();

            inertia.redirectWithFragment(res, "/page#section");

            assertThat(res.getStatus()).isEqualTo(409);
            assertThat(res.getHeader("X-Inertia-Redirect")).isEqualTo("/page#section");
        }
    }
}
