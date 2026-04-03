package com.wpanther.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.notification.infrastructure.adapter.out.notification.EmailNotificationSenderAdapter;
import com.wpanther.notification.infrastructure.adapter.out.notification.WebhookNotificationSenderAdapter;
import com.wpanther.notification.integration.config.ConsumerTestConfiguration;
import com.wpanther.notification.integration.config.TestKafkaProducerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9093",
        "KAFKA_BROKERS=localhost:9093"
    }
)
@ActiveProfiles("consumer-test")
@Import({TestKafkaProducerConfig.class, ConsumerTestConfiguration.class})
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractKafkaConsumerTest {

    @Autowired
    protected KafkaTemplate<String, String> testKafkaTemplate;

    @Autowired
    protected JdbcTemplate testJdbcTemplate;

    @MockBean
    protected EmailNotificationSenderAdapter emailNotificationSenderAdapter;

    @MockBean
    protected WebhookNotificationSenderAdapter webhookNotificationSenderAdapter;

    protected ObjectMapper objectMapper;

    @BeforeAll
    void setupObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void setupMocksAndCleanup() throws Exception {
        // Configure mock senders
        when(emailNotificationSenderAdapter.supports(any())).thenReturn(true);
        doNothing().when(emailNotificationSenderAdapter).send(any());

        when(webhookNotificationSenderAdapter.supports(any())).thenReturn(false);

        // Clean database
        testJdbcTemplate.execute("DELETE FROM notifications");
    }

    protected void sendEvent(String topic, String key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            testKafkaTemplate.send(topic, key, json).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send event to topic: " + topic, e);
        }
    }

    protected Map<String, Object> awaitNotificationByDocumentId(String documentId) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> {
                   Map<String, Object> n = getNotificationByDocumentId(documentId);
                   return n != null && "SENT".equals(n.get("status"));
               });
        return getNotificationByDocumentId(documentId);
    }

    protected Map<String, Object> awaitNotificationByCorrelationId(String correlationId) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> {
                   Map<String, Object> n = getNotificationByCorrelationId(correlationId);
                   return n != null && "SENT".equals(n.get("status"));
               });
        return getNotificationByCorrelationId(correlationId);
    }

    protected void awaitNotificationCount(int expectedCount) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getNotificationCount() >= expectedCount);
    }

    protected int getNotificationCount() {
        Integer count = testJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notifications", Integer.class);
        return count != null ? count : 0;
    }

    protected Map<String, Object> getNotificationByDocumentId(String documentId) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
            "SELECT * FROM notifications WHERE document_id = ?", documentId);
        return results.isEmpty() ? null : results.get(0);
    }

    protected Map<String, Object> getNotificationByCorrelationId(String correlationId) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
            "SELECT * FROM notifications WHERE correlation_id = ?", correlationId);
        return results.isEmpty() ? null : results.get(0);
    }

    protected void assertNoNotificationCreatedAfterWait() {
        await().during(15, TimeUnit.SECONDS)
               .atMost(20, TimeUnit.SECONDS)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getNotificationCount() == 0);
    }
}
