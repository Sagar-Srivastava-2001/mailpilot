package com.sagar.emailsender.api;

import com.sagar.emailsender.model.*;
import com.sagar.emailsender.service.EmailCampaignService;
import com.sagar.emailsender.service.GoogleSheetsContactReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import com.sagar.emailsender.service.CompanyEmailReportService;


@RestController
@RequestMapping("/api/google-sheets")
public class GoogleSheetsController {
    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsController.class);
    private final GoogleSheetsContactReader sheetsReader;
    private final EmailCampaignService campaignService;
    private final CompanyEmailReportService companyEmailReportService;

    public GoogleSheetsController(
            GoogleSheetsContactReader sheetsReader,
            EmailCampaignService campaignService,
            CompanyEmailReportService companyEmailReportService
    ) {
        this.sheetsReader = sheetsReader;
        this.campaignService = campaignService;
        this.companyEmailReportService = companyEmailReportService;
    }

    @Value("${google.sheet.url}")
    private String SAGAR_SPREADSHEET_URL;

    private String resolveSpreadsheetUrl(String spreadsheetUrl) {
        if (spreadsheetUrl == null
                || spreadsheetUrl.isBlank()
                || spreadsheetUrl.trim().equalsIgnoreCase("sagar")) {
            return SAGAR_SPREADSHEET_URL;
        }

        return spreadsheetUrl.trim();
    }

    @GetMapping("/tabs")
    public GoogleSheetsTabsResponse listTabs(@RequestParam(value = "spreadsheetUrl", required = false) String spreadsheetUrl) {
        return sheetsReader.listTabs(
                resolveSpreadsheetUrl(spreadsheetUrl)
        );
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse preview(
            @RequestParam(value = "spreadsheetUrl", required = false) String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body) {
        ExcelImportResult imported = sheetsReader.readSelectedTabs(
                resolveSpreadsheetUrl(spreadsheetUrl),
                sheetNames
        );
        return campaignService.previewImported(imported, subject, body);
    }

    @PostMapping(
            value = "/company-report",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CompanyEmailReport companyEmailReport(
            @RequestParam(value = "spreadsheetUrl", required = false) String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames
    ) {
        ExcelImportResult imported = sheetsReader.readSelectedTabs(
                resolveSpreadsheetUrl(spreadsheetUrl),
                sheetNames
        );

        return companyEmailReportService.generate(imported);
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftResponse createDrafts(
            @RequestParam(value = "spreadsheetUrl", required = false) String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(name = "resume", required = false) MultipartFile resume) {
        ExcelImportResult imported = sheetsReader.readSelectedTabs(resolveSpreadsheetUrl(spreadsheetUrl), sheetNames);
        return campaignService.createDraftsImported(imported, resume, subject, body);
    }


    @PostMapping(
            value = "/send",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public SendResponse sendEmails(
            @RequestParam(value = "spreadsheetUrl", required = false) String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(name = "resume", required = false)
            MultipartFile resume) {
        ExcelImportResult imported =
                sheetsReader.readSelectedTabs(resolveSpreadsheetUrl(spreadsheetUrl), sheetNames);

        return campaignService.sendEmailsImported(
                imported, resume, subject, body
        );
    }

}
