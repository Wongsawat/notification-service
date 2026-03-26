package com.wpanther.notification.infrastructure.adapter.out.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

/**
 * Template rendering engine using Thymeleaf
 */
@Component("notificationTemplateEngine")
@RequiredArgsConstructor
@Slf4j
public class TemplateEngine {

    private final SpringTemplateEngine thymeleafEngine;

    /**
     * Render template with variables
     */
    public String render(String templateName, Map<String, Object> variables) {
        try {
            log.debug("Rendering template: {} with {} variables", templateName, variables.size());

            Context context = new Context();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }

            String result = thymeleafEngine.process(templateName, context);

            log.debug("Template rendered successfully: {}", templateName);
            return result;

        } catch (Exception e) {
            log.error("Failed to render template: {}", templateName, e);
            throw new TemplateException("Failed to render template: " + templateName, e);
        }
    }

    /**
     * Exception for template rendering failures
     */
    public static class TemplateException extends RuntimeException {
        public TemplateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
