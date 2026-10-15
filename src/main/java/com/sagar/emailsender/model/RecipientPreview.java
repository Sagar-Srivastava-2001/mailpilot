
package com.sagar.emailsender.model;

public record RecipientPreview(
        int rowNumber,
        String name,
        String email,
        String company,
        String sourceSheet,
        String personalizedSubject,
        String personalizedBody
) {
    public RecipientPreview(
            int rowNumber,
            String name,
            String email,
            String company,
            String sourceSheet
    ) {
        this(rowNumber, name, email, company, sourceSheet, "", "");
    }

    public RecipientPreview(
            int rowNumber,
            String name,
            String email,
            String company
    ) {
        this(rowNumber, name, email, company, "", "", "");
    }
}
