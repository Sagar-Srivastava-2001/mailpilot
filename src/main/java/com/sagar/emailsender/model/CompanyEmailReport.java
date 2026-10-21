package com.sagar.emailsender.model;

import java.util.List;

public record CompanyEmailReport(
        int totalEmails,
        int uniqueCompanies,
        int issueCount,
        List<CompanyEmailCount> companies
) {

    public CompanyEmailReport {
        companies = List.copyOf(companies);
    }
}