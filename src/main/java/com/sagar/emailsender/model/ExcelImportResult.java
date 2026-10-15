package com.sagar.emailsender.model;

import java.util.List;

public record ExcelImportResult(List<EmailContact> contacts, List<ImportIssue> issues) {
    public ExcelImportResult {
        contacts = List.copyOf(contacts);
        issues = List.copyOf(issues);
    }
}
