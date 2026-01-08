package com.longdx.silre_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
     * Security filter chain configuration
     * Currently allows all requests - will be configured with JWT later
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for API (will use JWT)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // Allow all requests for now
            );
        
        return http.build();
    }
}
