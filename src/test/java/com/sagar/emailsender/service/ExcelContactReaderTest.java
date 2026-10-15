package com.sagar.emailsender.service;

import com.sagar.emailsender.model.ExcelImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelContactReaderTest {
    private final ExcelContactReader reader = new ExcelContactReader();

    @Test
    void readsContactsAndFlagsBadOrDuplicateRows() throws Exception {
        byte[] workbookBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Contacts");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Email Address");
            header.createCell(2).setCellValue("Company");

            addRow(sheet, 1, "Barnali", "barnali@example.com", "Blinkit");
            addRow(sheet, 2, "Duplicate", "barnali@example.com", "Blinkit");
            addRow(sheet, 3, "Bad email", "not-an-email", "Blinkit");

            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "contacts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);
        ExcelImportResult result = reader.read(file);

        assertEquals(1, result.contacts().size());
        assertEquals("barnali@example.com", result.contacts().get(0).email());
        assertEquals(2, result.issues().size());
    }

    private void addRow(Sheet sheet, int rowIndex, String name, String email, String company) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(email);
        row.createCell(2).setCellValue(company);
    }
}
