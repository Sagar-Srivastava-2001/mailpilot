package com.sagar.emailsender.model;

import java.util.List;

public record PreviewResponse(
        int recipientCount,
        int issueCount,
        int batchLimit,
        long delayMs,
        List<RecipientPreview> recipients,
        List<ImportIssue> issues,
        String sampleTo,
        String sampleSubject,
        String sampleBody) {
}
