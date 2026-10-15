package com.sagar.emailsender.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.sagar.emailsender.gmail.GmailOAuthService;
import com.sagar.emailsender.model.EmailContact;
import com.sagar.emailsender.model.ExcelImportResult;
import com.sagar.emailsender.model.GoogleSheetTab;
import com.sagar.emailsender.model.GoogleSheetsTabsResponse;
import com.sagar.emailsender.model.ImportIssue;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GoogleSheetsContactReader {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{10,}$");

    private final GmailOAuthService oauthService;

    public GoogleSheetsContactReader(GmailOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    public GoogleSheetsTabsResponse listTabs(String spreadsheetUrlOrId) {
        String spreadsheetId = extractSpreadsheetId(spreadsheetUrlOrId);
        try {
            Spreadsheet spreadsheet = sheetsClient().spreadsheets().get(spreadsheetId)
                    .setFields("spreadsheetId,properties(title),sheets(properties(sheetId,title,index))")
                    .execute();
            SpreadsheetProperties properties = spreadsheet.getProperties();
            String title = properties == null ? "Google spreadsheet" : properties.getTitle();
            List<GoogleSheetTab> tabs = spreadsheet.getSheets() == null ? List.of() : spreadsheet.getSheets().stream()
                    .filter(sheet -> sheet.getProperties() != null && StringUtils.hasText(sheet.getProperties().getTitle()))
                    .map(sheet -> new GoogleSheetTab(
                            sheet.getProperties().getTitle(),
                            sheet.getProperties().getSheetId() == null ? -1 : sheet.getProperties().getSheetId(),
                            sheet.getProperties().getIndex() == null ? 0 : sheet.getProperties().getIndex()))
                    .toList();
            if (tabs.isEmpty()) {
                throw new IllegalArgumentException("No tabs were found in this Google spreadsheet.");
            }
            return new GoogleSheetsTabsResponse(spreadsheetId, title == null ? "Google spreadsheet" : title, tabs);
        } catch (GoogleJsonResponseException exception) {
            throw translateGoogleError(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not connect to the Google Sheets API. Check your connection and reconnect Google if needed.", exception);
        }
    }

    public ExcelImportResult readSelectedTabs(String spreadsheetUrlOrId, List<String> selectedTabNames) {
        if (selectedTabNames == null || selectedTabNames.isEmpty()) {
            throw new IllegalArgumentException("Select at least one Google Sheets tab.");
        }
        GoogleSheetsTabsResponse spreadsheet = listTabs(spreadsheetUrlOrId);
        Set<String> availableTabs = new HashSet<>();
        spreadsheet.tabs().forEach(tab -> availableTabs.add(tab.title()));
        for (String tabName : selectedTabNames) {
            if (!StringUtils.hasText(tabName) || !availableTabs.contains(tabName)) {
                throw new IllegalArgumentException("Selected tab '" + tabName + "' was not found in the spreadsheet. Reload the tabs and select again.");
            }
        }

        Sheets client;
        try {
            client = sheetsClient();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the Google Sheets API client. Reconnect Google and try again.", exception);
        }

        List<EmailContact> contacts = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenSheetNames = new HashSet<>();

        for (String sheetName : selectedTabNames) {
            if (!seenSheetNames.add(sheetName)) {
                continue;
            }
            List<List<Object>> values;
            try {
                String quotedSheetName = "'" + sheetName.replace("'", "''") + "'!A:ZZ";
                ValueRange response = client.spreadsheets().values().get(spreadsheet.spreadsheetId(), quotedSheetName)
                        .setMajorDimension("ROWS")
                        .setValueRenderOption("FORMATTED_VALUE")
                        .execute();
                values = response.getValues();
            } catch (GoogleJsonResponseException exception) {
                throw translateGoogleError(exception);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read tab '" + sheetName + "'. Check the spreadsheet permissions and try again.", exception);
            }

            if (values == null || values.isEmpty()) {
                issues.add(new ImportIssue(1, "Sheet is empty or has no header row.", sheetName));
                continue;
            }

            Map<String, Integer> columns = findColumns(values.get(0));
            String emailKey = columns.containsKey("emailaddress") ? "emailaddress" : "email";
            List<String> missing = new ArrayList<>();
            if (!columns.containsKey("name")) missing.add("Name");
            if (!columns.containsKey(emailKey)) missing.add("Email Address");
            if (!columns.containsKey("company")) missing.add("Company");
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Tab '" + sheetName + "' is missing required column(s): "
                        + String.join(", ", missing) + ". Expected headers: Name, Email Address, Company.");
            }

            for (int index = 1; index < values.size(); index++) {
                List<Object> row = values.get(index);
                if (isBlankRow(row)) continue;
                int rowNumber = index + 1;
                String name = valueAt(row, columns.get("name"));
                String email = valueAt(row, columns.get(emailKey)).toLowerCase(Locale.ROOT);
                String company = valueAt(row, columns.get("company"));
                if (name.isBlank() || email.isBlank() || company.isBlank()) {
                    issues.add(new ImportIssue(rowNumber, "Name, Email Address, and Company are all required.", sheetName));
                    continue;
                }
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    issues.add(new ImportIssue(rowNumber, "Email Address does not look valid: " + email, sheetName));
                    continue;
                }
                if (!seenEmails.add(email)) {
                    issues.add(new ImportIssue(rowNumber, "Duplicate email address across selected tabs: " + email, sheetName));
                    continue;
                }
                contacts.add(new EmailContact(name, email, company, rowNumber, sheetName));
            }
        }
        if (contacts.isEmpty() && issues.isEmpty()) {
            throw new IllegalArgumentException("No contact rows were found below the headers in the selected tabs.");
        }
        return new ExcelImportResult(contacts, issues);
    }

    static String extractSpreadsheetId(String input) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Paste a Google Sheets URL or spreadsheet ID.");
        }
        String value = input.trim();
        if (ID_PATTERN.matcher(value).matches()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("docs.google.com") || host.equalsIgnoreCase("sheets.google.com"))) {
                throw new IllegalArgumentException("Enter a Google Sheets URL, such as https://docs.google.com/spreadsheets/d/…/edit.");
            }
            String[] parts = uri.getPath().split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("d".equals(parts[i]) && ID_PATTERN.matcher(parts[i + 1]).matches()) {
                    return parts[i + 1];
                }
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Enter a Google Sheets URL")) {
                throw exception;
            }
        }
        throw new IllegalArgumentException("Could not find a spreadsheet ID in that URL. Copy the full URL from Google Sheets.");
    }

    private Sheets sheetsClient() throws IOException {
        return oauthService.sheetsClient();
    }

    private Map<String, Integer> findColumns(List<Object> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String normalized = asText(headers.get(index)).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!normalized.isBlank()) columns.putIfAbsent(normalized, index);
        }
        return columns;
    }

    private String valueAt(List<Object> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) return "";
        return asText(row.get(index)).trim();
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlankRow(List<Object> row) {
        return row == null || row.stream().allMatch(value -> !StringUtils.hasText(asText(value)));
    }

    private RuntimeException translateGoogleError(GoogleJsonResponseException exception) {
        int status = exception.getStatusCode();
        if (status == 401 || status == 403) {
            return new IllegalStateException("Google denied access to the spreadsheet. Enable Google Sheets API, add the spreadsheets.readonly scope in Google Auth Platform → Data Access, then delete the local token folder and reconnect Google. Also ensure this spreadsheet is accessible to the connected account.", exception);
        }
        if (status == 404) {
            return new IllegalArgumentException("Spreadsheet not found. Check that the URL is correct and the connected Google account has access.", exception);
        }
        return new IllegalStateException("Google Sheets API returned HTTP " + status + ". Try again in a moment.", exception);
    }
}
