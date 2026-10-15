package com.sagar.emailsender.model;

import java.util.List;

public record SendResponse(
        int attempted,
        int sent,
        int failed,
        List<SendItemResult> results
) {
    public record SendItemResult(
            String name,
            String email,
            String status,
            String detail,
            String messageId
    ) {
    }
}