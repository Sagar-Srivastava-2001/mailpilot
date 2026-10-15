package com.sagar.emailsender.service;

import com.sagar.emailsender.model.EmailContact;
import org.springframework.stereotype.Service;

@Service
public class EmailTemplateService {
    public String render(String template, EmailContact contact) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{{name}}", contact.name())
                .replace("{{company}}", contact.company())
                .replace("{{email}}", contact.email());
    }
}
