package com.sagar.emailsender.model;

public record GmailStatusResponse(boolean credentialsConfigured, boolean connected, String message) {
}
