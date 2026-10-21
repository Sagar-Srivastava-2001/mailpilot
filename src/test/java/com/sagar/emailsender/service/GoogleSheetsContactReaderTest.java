package com.sagar.emailsender.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleSheetsContactReaderTest {
    @Test
    void extractsSpreadsheetIdFromGoogleSheetsUrl() {
        String id = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
        assertEquals(id, GoogleSheetsContactReader.extractSpreadsheetId(
                "https://docs.google.com/spreadsheets/d/" + id + "/123edit?gid=123#gid=123"));
    }

    @Test
    void acceptsSpreadsheetIdDirectly() {
        String id = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
        assertEquals(id, GoogleSheetsContactReader.extractSpreadsheetId(id));
    }

    @Test
    void rejectsNonGoogleUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> GoogleSheetsContactReader.extractSpreadsheetId("https://example.com/spreadsheets/d/abcdefghijk"));
    }
}
