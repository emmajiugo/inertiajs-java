package io.inertia.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.inertia.core.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(InertiaEngine.class)
@EnableConfigurationProperties(InertiaProperties.class)
public class InertiaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InertiaEngine inertiaEngine(InertiaProperties properties,
                                       ObjectProvider<SharedPropsResolver> resolvers,
                                       ObjectProvider<ObjectMapper> objectMapper) {
        InertiaConfig config = InertiaConfig.builder()
                .version(properties.getVersion())
                .templateResolver(new ClasspathTemplateResolver(properties.getTemplatePath()))
                .jsonSerializer(new JacksonJsonSerializer(
                        objectMapper.getIfAvailable(ObjectMapper::new)))
                .build();

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
