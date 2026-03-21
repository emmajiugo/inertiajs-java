package io.github.emmajiugo.inertia.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.emmajiugo.inertia.core.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnClass(InertiaEngine.class)
@EnableConfigurationProperties(InertiaProperties.class)
public class InertiaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "inertia.ssr.url")
    public SsrClient ssrClient(InertiaProperties properties,
                                ObjectProvider<ObjectMapper> objectMapper) {
        return new HttpSsrClient(
                properties.getSsr().getUrl(),
                java.time.Duration.ofMillis(properties.getSsr().getTimeout()),
                objectMapper.getIfAvailable(() -> JsonMapper.builder().findAndAddModules().build()));
    }

    @Bean
    @ConditionalOnMissingBean
    public InertiaEngine inertiaEngine(InertiaProperties properties,
                                       ObjectProvider<SharedPropsResolver> resolvers,
                                       ObjectProvider<ObjectMapper> objectMapper,
                                       ObjectProvider<SsrClient> ssrClient) {
        InertiaConfig.Builder configBuilder = InertiaConfig.builder()
                .templateResolver(new ClasspathTemplateResolver(
                        properties.getTemplatePath(), properties.isCacheTemplates()))
                .jsonSerializer(new JacksonJsonSerializer(
                        objectMapper.getIfAvailable(() -> JsonMapper.builder().findAndAddModules().build())));

        // Version resolution: explicit > Vite manifest > fallback "1"
        if (properties.getVersion() != null) {
            configBuilder.version(properties.getVersion());
        } else {
            configBuilder.versionSupplier(
                    ViteManifestVersionResolver.lazy(properties.getManifestPath()));
        }

        // SSR configuration
        SsrClient client = ssrClient.getIfAvailable();
        if (client != null) {
            configBuilder.ssrClient(client);
            configBuilder.ssrEnabled(properties.getSsr().isEnabled());
            configBuilder.ssrFailOnError(properties.getSsr().isFailOnError());
        }

        InertiaConfig config = configBuilder.build();

        InertiaEngine engine = new InertiaEngine(config);
        resolvers.orderedStream().forEach(engine::addSharedPropsResolver);
        return engine;
    }

    @Bean
    @ConditionalOnMissingBean
    public Inertia inertia(InertiaEngine engine) {
        return new Inertia(engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public InertiaValidationSharedProps inertiaValidationSharedProps() {
        return new InertiaValidationSharedProps();
    }

    @Bean
    public InertiaHandlerInterceptor inertiaHandlerInterceptor(InertiaEngine engine) {
        return new InertiaHandlerInterceptor(engine);
    }

    @Bean
    public WebMvcConfigurer inertiaWebMvcConfigurer(InertiaHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
