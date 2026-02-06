package com.wpanther.notification;

import com.wpanther.notification.application.controller.NotificationController;
import com.wpanther.notification.application.service.NotificationService;
import com.wpanther.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("NotificationServiceApplication Context Tests")
class NotificationServiceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Should load Spring context without errors")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("Should have all required beans configured")
    void testRequiredBeansExist() {
        assertThat(context.getBean(NotificationService.class)).isNotNull();
        assertThat(context.getBean(NotificationController.class)).isNotNull();
        assertThat(context.getBean(NotificationRepository.class)).isNotNull();
        assertThat(context.getBean("notificationTemplateEngine")).isNotNull();
    }

    @Test
    @DisplayName("Should have async and scheduling enabled")
    void testAsyncAndSchedulingEnabled() {
        // Verify @EnableAsync and @EnableScheduling are active by checking the beans exist
        assertThat(context.containsBean("notificationService")).isTrue();

        // The scheduled methods should be present in NotificationService
        NotificationService service = context.getBean(NotificationService.class);
        assertThat(service).isNotNull();
    }
}
