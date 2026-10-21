package com.sagar.emailsender.model;

import java.util.List;

public record CompanyEmailCount(
        String company,
        int emailCount,
        List<CompanySourceAccount> sourceAccounts
) {
    public CompanyEmailCount {
        sourceAccounts = List.copyOf(sourceAccounts);
    }
}