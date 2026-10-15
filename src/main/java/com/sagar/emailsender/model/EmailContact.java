package com.sagar.emailsender.model;

public record EmailContact(String name, String email, String company, int rowNumber, String sourceSheet) {
    public EmailContact(String name, String email, String company, int rowNumber) {
        this(name, email, company, rowNumber, "");
    }
}
