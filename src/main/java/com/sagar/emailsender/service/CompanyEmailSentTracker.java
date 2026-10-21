package com.sagar.emailsender.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.sagar.emailsender.gmail.GmailOAuthService;
import com.sagar.emailsender.model.CompanyEmailCount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompanyEmailSentTracker {

    private static final Pattern GID_PATTERN =
            Pattern.compile("(?:[?&#])gid=(\\d+)");

    private final GmailOAuthService oauthService;
    private final String trackingSpreadsheetUrl;

    public CompanyEmailSentTracker(
            GmailOAuthService oauthService,
            @Value("${google.sheet.tracking-url}") String trackingSpreadsheetUrl
    ) {
        this.oauthService = oauthService;
        this.trackingSpreadsheetUrl = trackingSpreadsheetUrl;
    }

    /**
     * Replaces the Email Sent cell with the email count from the
     * newly generated company report.
     *
     * Companies not present in the tracking sheet are ignored.
     * Duplicate matching company rows in the tracking sheet are all updated.
     */
    public void updateCompanyCounts(
            List<CompanyEmailCount> companies
    ) throws IOException {

        if (companies == null || companies.isEmpty()) {
            return;
        }

        if (!StringUtils.hasText(trackingSpreadsheetUrl)) {
            throw new IllegalStateException(
                    "Google Sheets tracking URL is not configured."
            );
        }

        String spreadsheetId =
                GoogleSheetsContactReader.extractSpreadsheetId(
                        trackingSpreadsheetUrl
                );

        Sheets sheets = oauthService.sheetsClient();

        // Find the tracking tab using gid from the configured URL.
        Spreadsheet spreadsheet = sheets.spreadsheets()
                .get(spreadsheetId)
                .setFields("sheets(properties(sheetId,title,index))")
                .execute();

        String tabName = resolveTrackingTabName(spreadsheet);

        // Quote the tab name safely for Google Sheets A1 notation.
        String quotedTab = "'" + tabName.replace("'", "''") + "'!";

        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, quotedTab + "A:B")
                .setMajorDimension("ROWS")
                .setValueRenderOption("FORMATTED_VALUE")
                .execute();

        List<List<Object>> rows = response.getValues();

        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException(
                    "The tracking spreadsheet tab has no header row."
            );
        }

        List<Object> headers = rows.get(0);

        int companyColumn = findColumn(headers, "Target Companies");
        int emailSentColumn = findColumn(headers, "Email Sent");

        if (companyColumn < 0 || emailSentColumn < 0) {
            throw new IllegalStateException(
                    "The tracking sheet must contain 'Target Companies' "
                            + "and 'Email Sent' headers in row 1."
            );
        }

        // Use the report counts directly. Do not add to existing values.
        Map<String, Integer> reportCounts = new HashMap<>();

        for (CompanyEmailCount company : companies) {
            if (company != null && StringUtils.hasText(company.company())) {
                reportCounts.put(
                        normalizeCompany(company.company()),
                        company.emailCount()
                );
            }
        }

        List<ValueRange> updates = new ArrayList<>();

        // Index 0 is the header row; data starts at spreadsheet row 2.
        for (int index = 1; index < rows.size(); index++) {
            List<Object> row = rows.get(index);

            String trackerCompany = valueAt(row, companyColumn);
            if (!StringUtils.hasText(trackerCompany)) {
                continue;
            }

            Integer exactCount =
                    reportCounts.get(normalizeCompany(trackerCompany));

            // Company not in the current report: leave it unchanged.
            if (exactCount == null) {
                continue;
            }

            int spreadsheetRowNumber = index + 1;

            String cellRange = quotedTab
                    + columnToLetters(emailSentColumn)
                    + spreadsheetRowNumber;

            updates.add(
                    new ValueRange()
                            .setRange(cellRange)
                            .setValues(
                                    List.of(List.<Object>of(exactCount))
                            )
            );
        }

        if (updates.isEmpty()) {
            return;
        }

        BatchUpdateValuesRequest request =
                new BatchUpdateValuesRequest()
                        .setValueInputOption("RAW")
                        .setData(updates);

        sheets.spreadsheets()
                .values()
                .batchUpdate(spreadsheetId, request)
                .execute();
    }

    private String resolveTrackingTabName(Spreadsheet spreadsheet) {
        List<Sheet> tabs = spreadsheet.getSheets();

        if (tabs == null || tabs.isEmpty()) {
            throw new IllegalStateException(
                    "No tabs were found in the tracking spreadsheet."
            );
        }

        Matcher matcher = GID_PATTERN.matcher(trackingSpreadsheetUrl);

        if (matcher.find()) {
            int requestedGid = Integer.parseInt(matcher.group(1));

            return tabs.stream()
                    .filter(sheet -> sheet.getProperties() != null)
                    .filter(sheet -> Objects.equals(
                            sheet.getProperties().getSheetId(),
                            requestedGid
                    ))
                    .map(sheet -> sheet.getProperties().getTitle())
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Could not find tracking tab with gid="
                                    + requestedGid
                    ));
        }

        // When no gid is provided, use the first tab.
        return tabs.stream()
                .filter(sheet -> sheet.getProperties() != null)
                .min(Comparator.comparingInt(sheet -> {
                    Integer index = sheet.getProperties().getIndex();
                    return index == null ? Integer.MAX_VALUE : index;
                }))
                .map(sheet -> sheet.getProperties().getTitle())
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not determine the tracking tab name."
                ));
    }

    private int findColumn(List<Object> headers, String expectedHeader) {
        String expected = normalizeHeader(expectedHeader);

        for (int index = 0; index < headers.size(); index++) {
            if (expected.equals(normalizeHeader(
                    headers.get(index) == null
                            ? ""
                            : headers.get(index).toString()
            ))) {
                return index;
            }
        }

        return -1;
    }

    private String valueAt(List<Object> row, int columnIndex) {
        if (row == null
                || columnIndex < 0
                || columnIndex >= row.size()
                || row.get(columnIndex) == null) {
            return "";
        }

        return row.get(columnIndex).toString().trim();
    }

    private String normalizeCompany(String company) {
        return company == null
                ? ""
                : company.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeHeader(String header) {
        return header == null
                ? ""
                : header.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Converts a zero-based column index to Google Sheets column letters:
     * 0 -> A, 1 -> B, 25 -> Z, 26 -> AA.
     */
    private String columnToLetters(int zeroBasedColumn) {
        int column = zeroBasedColumn + 1;
        StringBuilder result = new StringBuilder();

        while (column > 0) {
            int remainder = (column - 1) % 26;
            result.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }

        return result.toString();
    }
}