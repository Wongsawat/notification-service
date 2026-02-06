package com.wpanther.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationChannel Enum Tests")
class NotificationChannelTest {

    @Test
    @DisplayName("Should have exactly 4 channel values")
    void testEnumCount() {
        assertThat(NotificationChannel.values()).hasSize(4);
    }

    @Test
    @DisplayName("Should contain all expected channel values")
    void testEnumValues() {
        assertThat(NotificationChannel.values())
            .containsExactlyInAnyOrder(
                NotificationChannel.EMAIL,
                NotificationChannel.SMS,
                NotificationChannel.WEBHOOK,
                NotificationChannel.IN_APP
            );
    }

    @Test
    @DisplayName("Should convert enum to string correctly")
    void testEnumToString() {
        assertThat(NotificationChannel.EMAIL.toString()).isEqualTo("EMAIL");
        assertThat(NotificationChannel.SMS.toString()).isEqualTo("SMS");
        assertThat(NotificationChannel.WEBHOOK.toString()).isEqualTo("WEBHOOK");
        assertThat(NotificationChannel.IN_APP.toString()).isEqualTo("IN_APP");
    }
}
