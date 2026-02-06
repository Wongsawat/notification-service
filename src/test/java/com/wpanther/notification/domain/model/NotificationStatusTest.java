package com.wpanther.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationStatus Enum Tests")
class NotificationStatusTest {

    @Test
    @DisplayName("Should have exactly 5 status values")
    void testEnumCount() {
        assertThat(NotificationStatus.values()).hasSize(5);
    }

    @Test
    @DisplayName("Should contain all expected status values")
    void testEnumValues() {
        assertThat(NotificationStatus.values())
            .containsExactlyInAnyOrder(
                NotificationStatus.PENDING,
                NotificationStatus.SENDING,
                NotificationStatus.SENT,
                NotificationStatus.FAILED,
                NotificationStatus.RETRYING
            );
    }

    @Test
    @DisplayName("Should convert enum to string correctly")
    void testEnumToString() {
        assertThat(NotificationStatus.PENDING.toString()).isEqualTo("PENDING");
        assertThat(NotificationStatus.SENDING.toString()).isEqualTo("SENDING");
        assertThat(NotificationStatus.SENT.toString()).isEqualTo("SENT");
        assertThat(NotificationStatus.FAILED.toString()).isEqualTo("FAILED");
        assertThat(NotificationStatus.RETRYING.toString()).isEqualTo("RETRYING");
    }
}
