package com.sagar.emailsender.model;

import java.util.List;

public record GoogleSheetsTabsResponse(String spreadsheetId, String spreadsheetTitle, List<GoogleSheetTab> tabs) {
    public GoogleSheetsTabsResponse {
        tabs = List.copyOf(tabs);
    }
}
