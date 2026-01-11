package com.longdx.silre_backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration to register interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TimezoneInterceptor timezoneInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(timezoneInterceptor)
                .addPathPatterns("/**")  // Apply to all paths
                .excludePathPatterns(
                    "/actuator/**",      // Exclude Actuator endpoints
                    "/swagger-ui/**",    // Exclude Swagger UI
                    "/v3/api-docs/**"    // Exclude OpenAPI docs
                );
    }
}
