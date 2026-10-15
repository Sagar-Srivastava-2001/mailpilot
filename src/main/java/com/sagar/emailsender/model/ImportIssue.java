package com.sagar.emailsender.model;

public record ImportIssue(int rowNumber, String reason, String sourceSheet) {
    public ImportIssue(int rowNumber, String reason) {
        this(rowNumber, reason, "");
    }
}
