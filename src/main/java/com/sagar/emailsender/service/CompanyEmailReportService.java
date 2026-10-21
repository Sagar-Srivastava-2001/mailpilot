package com.sagar.emailsender.service;

import com.sagar.emailsender.model.CompanyEmailCount;
import com.sagar.emailsender.model.CompanyEmailReport;
import com.sagar.emailsender.model.CompanySourceAccount;
import com.sagar.emailsender.model.EmailContact;
import com.sagar.emailsender.model.ExcelImportResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompanyEmailReportService {

    private static final Pattern SOURCE_ID_SUFFIX =
            Pattern.compile("-(\\d+)$");

    private final Map<String, String> sourceEmails;

    public CompanyEmailReportService(
            @Value("${email.id-1:}") String email1,
            @Value("${email.id-2:}") String email2,
            @Value("${email.id-3:}") String email3,
            @Value("${email.id-4:}") String email4
    ) {
        // Constructor injection ensures values are available
        // before the source-account map is initialized.
        this.sourceEmails = Map.of(
                "-1", normalizeEmail(email1),
                "-2", normalizeEmail(email2),
                "-3", normalizeEmail(email3),
                "-4", normalizeEmail(email4)
        );
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    public CompanyEmailReport generate(ExcelImportResult imported) {

        Map<String, CompanyAccumulator> companies =
                new LinkedHashMap<>();

        for (EmailContact contact : imported.contacts()) {

            String company = contact.company()
                    .trim()
                    .replaceAll("\\s+", " ");

            String normalizedCompany =
                    company.toLowerCase(Locale.ROOT);

            CompanyAccumulator accumulator =
                    companies.computeIfAbsent(
                            normalizedCompany,
                            ignored -> new CompanyAccumulator(company)
                    );

            accumulator.emailCount++;
            accumulator.addSourceAccount(contact.sourceSheet());
        }

        List<CompanyEmailCount> reportRows =
                companies.values()
                        .stream()
                        .map(CompanyAccumulator::toReportRow)
                        .sorted(
                                Comparator.comparingInt(
                                                CompanyEmailCount::emailCount
                                        )
                                        .reversed()
                                        .thenComparing(
                                                CompanyEmailCount::company,
                                                String.CASE_INSENSITIVE_ORDER
                                        )
                        )
                        .toList();

        return new CompanyEmailReport(
                imported.contacts().size(),
                reportRows.size(),
                imported.issues().size(),
                reportRows
        );
    }

    private CompanySourceAccount sourceAccountFor(String sourceSheet) {

        String sheetName = sourceSheet == null
                ? ""
                : sourceSheet.trim();

        Matcher matcher = SOURCE_ID_SUFFIX.matcher(sheetName);

        if (!matcher.find()) {
            return new CompanySourceAccount(
                    "Unknown",
                    "Not mapped"
            );
        }

        String sourceId = "-" + matcher.group(1);

        String email = sourceEmails.get(sourceId);

        if (email == null) {
            email = "Not mapped";
        } else if (email.isBlank()) {
            email = "Not configured";
        }

        return new CompanySourceAccount(sourceId, email);
    }

    private final class CompanyAccumulator {

        private final String company;
        private int emailCount;

        private final Map<String, CompanySourceAccount> sourceAccounts =
                new LinkedHashMap<>();

        private CompanyAccumulator(String company) {
            this.company = company;
        }

        private void addSourceAccount(String sourceSheet) {

            CompanySourceAccount account =
                    sourceAccountFor(sourceSheet);

            sourceAccounts.putIfAbsent(account.id(), account);
        }

        private CompanyEmailCount toReportRow() {

            return new CompanyEmailCount(
                    company,
                    emailCount,
                    new ArrayList<>(sourceAccounts.values())
            );
        }
    }
}