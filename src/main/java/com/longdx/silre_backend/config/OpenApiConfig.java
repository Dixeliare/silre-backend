package com.longdx.silre_backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) Configuration
 * 
 * Swagger UI sẽ tự động có sẵn tại:
 * - http://localhost:8080/swagger-ui.html
 * - http://localhost:8080/swagger-ui/index.html
 * 
 * API Docs (JSON/YAML):
 * - http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Silre Backend API")
                        .version("1.0.0")
                        .description("""
                                Hybrid Social Platform Backend API
                                
                                **Features:**
                                - Forum System (Knowledge Base)
                                - Social Network (Communities & Personal Posts)
                                - User Identity (Dual-Key: TSID + NanoID)
                                - Smart Feed Algorithm (Gravity Score)
                                
                                **Authentication:**
                                - JWT Token-based authentication
                                - Use `/api/v1/auth/login` to get access token
                                
                                **ID Strategy:**
                                - Internal IDs: TSID (Time-Sorted ID) - 64-bit integers
                                - Public IDs: NanoID (Short strings) - Used in URLs and APIs
                                """)
                        .contact(new Contact()
                                .name("LongDx")
                                .email("support@silre.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://silre.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.silre.com")
                                .description("Production Server")
                ));
    }
}
