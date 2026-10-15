package com.sagar.emailsender.service;

import com.sagar.emailsender.model.EmailContact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailTemplateServiceTest {
    private final EmailTemplateService templateService = new EmailTemplateService();

    @Test
    void replacesSupportedPlaceholders() {
        EmailContact contact = new EmailContact("Barnali Handique", "barnali@example.com", "Blinkit", 2);
        String rendered = templateService.render("Hi {{name}} at {{company}} ({{email}})", contact);
        assertEquals("Hi Barnali Handique at Blinkit (barnali@example.com)", rendered);
    }
}
