package com.invoice.notification.infrastructure.config;

import com.invoice.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.invoice.notification.infrastructure.messaging.PdfGeneratedEvent;
import com.invoice.notification.infrastructure.messaging.PdfSignedEvent;
import com.invoice.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for event-driven notifications
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Common consumer configuration
     */
    private Map<String, Object> consumerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.invoice.*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return config;
    }

    /**
     * Consumer factory for TaxInvoiceProcessedEvent
     */
    @Bean
    public ConsumerFactory<String, TaxInvoiceProcessedEvent> taxInvoiceProcessedConsumerFactory() {
        Map<String, Object> config = consumerConfig();
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TaxInvoiceProcessedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory for TaxInvoiceProcessedEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaxInvoiceProcessedEvent>
    taxInvoiceProcessedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TaxInvoiceProcessedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(taxInvoiceProcessedConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        return factory;
    }

    /**
     * Consumer factory for InvoiceProcessedEvent
     */
    @Bean
    public ConsumerFactory<String, InvoiceProcessedEvent> invoiceProcessedConsumerFactory() {
        Map<String, Object> config = consumerConfig();
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InvoiceProcessedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory for InvoiceProcessedEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvoiceProcessedEvent>
    invoiceProcessedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InvoiceProcessedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(invoiceProcessedConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        return factory;
    }

    /**
     * Consumer factory for PdfGeneratedEvent
     */
    @Bean
    public ConsumerFactory<String, PdfGeneratedEvent> pdfGeneratedConsumerFactory() {
        Map<String, Object> config = consumerConfig();
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PdfGeneratedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory for PdfGeneratedEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PdfGeneratedEvent>
    pdfGeneratedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PdfGeneratedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(pdfGeneratedConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        return factory;
    }

    /**
     * Consumer factory for PdfSignedEvent
     */
    @Bean
    public ConsumerFactory<String, PdfSignedEvent> pdfSignedConsumerFactory() {
        Map<String, Object> config = consumerConfig();
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PdfSignedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory for PdfSignedEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PdfSignedEvent>
    pdfSignedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PdfSignedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(pdfSignedConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        return factory;
    }
}
