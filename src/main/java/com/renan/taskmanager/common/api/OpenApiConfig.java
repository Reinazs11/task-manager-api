package com.renan.taskmanager.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata and the Bearer JWT security scheme.
 *
 * <p><b>Why declare a security scheme explicitly?</b>
 * Without this, Swagger UI's "Authorize" button does not appear and consumers
 * cannot try protected endpoints without manually pasting headers. Declaring
 * {@code bearerAuth} once here lets every controller reference it via the
 * {@code @SecurityRequirement} annotation (added in Step 6b on protected
 * endpoints), so the UI prompts for a JWT and attaches it as
 * {@code Authorization: Bearer <token>} automatically.</p>
 *
 * <p><b>Why HTTP/bearer and not OAuth2?</b>
 * The API issues its own access tokens (see {@code JwtService}); there is no
 * authorization-code flow, no third-party authorization server. The
 * {@code http/bearer} scheme is the OpenAPI 3 idiomatic way to model a
 * stateless bearer token, and matches what the {@code JwtAuthenticationFilter}
 * actually consumes.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI taskManagerOpenApi(@Value("${spring.application.name:task-manager-api}") String appName,
                                     @Value("${app.api.version:0.1.0}") String appVersion) {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Manager API")
                        .description("REST API for task management with JWT authentication. "
                                + "Portfolio project demonstrating professional Spring Boot practices: "
                                + "simplified DDD, TDD, Testcontainers, and standardized error handling.")
                        .version(appVersion)
                        .contact(new Contact()
                                .name("Renan")
                                .url("https://github.com/renan-taskmanager"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token issued by POST /api/v1/auth/login. "
                                                + "Format: Bearer <token>")));
    }
}
