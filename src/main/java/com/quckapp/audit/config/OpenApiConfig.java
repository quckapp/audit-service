package com.quckapp.audit.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for QuckApp Audit Service.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "QuckApp Audit Service API",
        version = "1.0.0",
        description = """
            ## Audit Logging & Compliance Service

            The QuckApp Audit Service provides comprehensive audit logging, compliance reporting,
            and data retention capabilities for the QuckApp ecosystem.

            ### Features
            - **Audit Logging** - Capture and store all system events and user actions
            - **Search & Query** - Full-text search with Elasticsearch integration
            - **Retention Policies** - Configure data retention rules per workspace
            - **Compliance Reports** - Generate compliance reports (SOC2, GDPR, HIPAA)
            - **Real-time Events** - Kafka-based event streaming from all services

            ### Authentication
            This service is typically called by other microservices. Authentication is handled
            via API Key or internal service mesh authentication.

            ### Integration
            - Consumes events from Kafka topics
            - Stores logs in MySQL and Elasticsearch
            - Works with all QuckApp services for centralized audit
            """,
        contact = @Contact(
            name = "QuckApp Team",
            email = "support@quckapp.com",
            url = "https://quckapp.com"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(url = "/", description = "Audit Service Base Path"),
        @Server(url = "http://localhost:8084", description = "Local Development"),
        @Server(url = "https://api.quckapp.com/audit", description = "Production")
    },
    tags = {
        @Tag(name = "Audit Logs", description = "Audit log creation and search operations"),
        @Tag(name = "Retention Policies", description = "Data retention policy management"),
        @Tag(name = "Compliance Reports", description = "Compliance report generation and retrieval")
    }
)
public class OpenApiConfig {

    /**
     * Configure security schemes programmatically.
     * This ensures the Authorize button appears in Swagger UI.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                            JWT Bearer token authentication.

                            Obtain a token from the Auth Service by calling `/v1/login`.
                            Include the token in the Authorization header:
                            `Authorization: Bearer <token>`
                            """))
                .addSecuritySchemes("apiKey",
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
                        .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("""
                            API Key authentication for internal services.

                            Used for service-to-service communication within the QuckApp ecosystem.
                            Include the key in the X-API-Key header:
                            `X-API-Key: <your-api-key>`
                            """)));
    }
}
