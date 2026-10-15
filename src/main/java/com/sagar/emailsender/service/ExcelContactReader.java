package com.sagar.emailsender.service;

import com.sagar.emailsender.model.EmailContact;
import com.sagar.emailsender.model.ExcelImportResult;
import com.sagar.emailsender.model.ImportIssue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ExcelContactReader {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public ExcelImportResult read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a non-empty Excel file.");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new IllegalArgumentException("Upload an Excel workbook (.xlsx or .xls).");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("The workbook does not contain a sheet.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("The first sheet is empty.");
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = findColumns(headerRow, formatter);
            requireColumn(columns, "name", "Name");
            requireColumn(columns, "emailaddress", "Email Address");
            requireColumn(columns, "company", "Company");

            List<EmailContact> contacts = new ArrayList<>();
            List<ImportIssue> issues = new ArrayList<>();
            Set<String> seenEmails = new HashSet<>();

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }

                int excelRowNumber = rowIndex + 1;
                String name = readCell(row, columns.get("name"), formatter);
                String email = readCell(row, columns.get("emailaddress"), formatter).toLowerCase(Locale.ROOT);
                String company = readCell(row, columns.get("company"), formatter);

                if (name.isBlank() || email.isBlank() || company.isBlank()) {
                    issues.add(new ImportIssue(excelRowNumber, "Name, Email Address, and Company are all required.", sheet.getSheetName()));
                    continue;
                }
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    issues.add(new ImportIssue(excelRowNumber, "Email Address does not look valid: " + email, sheet.getSheetName()));
                    continue;
                }
                if (!seenEmails.add(email)) {
                    issues.add(new ImportIssue(excelRowNumber, "Duplicate email address: " + email, sheet.getSheetName()));
                    continue;
                }
                contacts.add(new EmailContact(name, email, company, excelRowNumber, sheet.getSheetName()));
            }

            if (contacts.isEmpty() && issues.isEmpty()) {
                throw new IllegalArgumentException("No contact rows were found below the header row.");
            }
            return new ExcelImportResult(contacts, issues);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the Excel workbook. Check that it is a valid Excel file.", e);
        } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException e) {
            throw new IllegalArgumentException("The uploaded file is not a valid Excel workbook.", e);
        }
    }

    private Map<String, Integer> findColumns(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        short lastCell = headerRow.getLastCellNum();
        for (int columnIndex = 0; columnIndex < lastCell; columnIndex++) {
            String header = formatter.formatCellValue(headerRow.getCell(columnIndex));
            String normalized = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!normalized.isBlank()) {
                columns.putIfAbsent(normalized, columnIndex);
            }
        }
        return columns;
    }

    private void requireColumn(Map<String, Integer> columns, String key, String label) {
        if (!columns.containsKey(key)) {
            throw new IllegalArgumentException("Missing required column: " + label + ". Expected headers: Name, Email Address, Company.");
        }
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(columnIndex)).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        short lastCell = row.getLastCellNum();
        for (int column = 0; column < lastCell; column++) {
            if (!formatter.formatCellValue(row.getCell(column)).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
