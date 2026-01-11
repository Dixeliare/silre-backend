package com.longdx.silre_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the application
 * 
 * Pattern:
 * - Provides PasswordEncoder bean for password hashing
 * - Configures HTTP security (currently allows all requests)
 * - TODO: Add JWT authentication, CORS, rate limiting
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password encoder bean using BCrypt
     * Used for hashing passwords before storing in database
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS configuration for API endpoints
     * Allows Swagger UI and frontend applications to call the API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow requests from Swagger UI (same origin) and common frontend origins
        configuration.setAllowedOrigins(List.of(
            "http://localhost:8080",  // Swagger UI (same origin)
            "http://localhost:3000",  // React dev server
            "http://localhost:5173",   // Vite dev server
            "http://127.0.0.1:8080",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173"
        ));
        
        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        
        // Allow all headers
        configuration.setAllowedHeaders(List.of("*"));
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * Security filter chain configuration
     * Currently allows all requests - will be configured with JWT later
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for API (will use JWT)
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
            .authorizeHttpRequests(auth -> auth
                // Swagger UI endpoints - allow public access
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                // Actuator endpoints - allow public access for now
                .requestMatchers("/actuator/**").permitAll()
                // Allow all requests for now - will be configured with JWT later
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}
