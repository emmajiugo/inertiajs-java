package io.inertia.spring;

import io.inertia.core.InertiaEngine;
import io.inertia.core.SsrClient;
import io.inertia.core.SsrResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InertiaAutoConfigurationSsrTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InertiaAutoConfiguration.class));

    @Test
    void createsSsrClientBeanWhenUrlIsSet() {
        contextRunner
                .withPropertyValues(
                        "inertia.ssr.url=http://127.0.0.1:13714",
                        "inertia.ssr.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SsrClient.class);
                    assertThat(context).hasSingleBean(InertiaEngine.class);
                    assertThat(context.getBean(InertiaEngine.class)
                            .getConfig().getSsrClient()).isNotNull();
                });
    }

    @Test
    void doesNotCreateSsrClientWhenUrlNotSet() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SsrClient.class);
                    assertThat(context).hasSingleBean(InertiaEngine.class);
                    assertThat(context.getBean(InertiaEngine.class)
                            .getConfig().getSsrClient()).isNull();
                });
    }

    @Test
    void allowsCustomSsrClientBeanOverride() {
        contextRunner
                .withUserConfiguration(CustomSsrClientConfig.class)
                .withPropertyValues("inertia.ssr.url=http://127.0.0.1:13714")
                .run(context -> {
                    assertThat(context).hasSingleBean(SsrClient.class);
                    assertThat(context.getBean(SsrClient.class))
                            .isInstanceOf(StubSsrClient.class);
                });
    }

    @Configuration
    static class CustomSsrClientConfig {
        @Bean
        SsrClient ssrClient() {
            return new StubSsrClient();
        }
    }

    static class StubSsrClient implements SsrClient {
        @Override
        public SsrResponse render(String pageJson) {
            return new SsrResponse(List.of(), "<div>stub</div>");
        }
    }
}
