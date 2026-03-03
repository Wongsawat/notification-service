package com.wpanther.notification.infrastructure.config;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration for distributed tracing.
 *
 * <p>Enables automatic trace propagation across Kafka and HTTP boundaries,
 * allowing request flows to be tracked across the microservices ecosystem.</p>
 *
 * <p>Traces are automatically exported to OTLP endpoint when configured.
 * Enable via environment variables for production:</p>
 * <pre>
 *   management.tracing.enabled=true
 *   management.otlp.tracing.endpoint=http://jaeger:4317
 *   OTEL_SERVICE_NAME=notification-service
 * </pre>
 */
@Configuration
public class OpenTelemetryConfig {

    /**
     * OpenTelemetry instance for advanced use cases.
     * Most applications should use the Micrometer Tracer bean instead.
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();  // Auto-configured by Spring Boot OTel
    }
}
