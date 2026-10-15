package com.sagar.emailsender.api;

import com.sagar.emailsender.model.*;
import com.sagar.emailsender.service.EmailCampaignService;
import com.sagar.emailsender.service.GoogleSheetsContactReader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/google-sheets")
public class GoogleSheetsController {
    private final GoogleSheetsContactReader sheetsReader;
    private final EmailCampaignService campaignService;

    public GoogleSheetsController(GoogleSheetsContactReader sheetsReader, EmailCampaignService campaignService) {
        this.sheetsReader = sheetsReader;
        this.campaignService = campaignService;
    }

    @GetMapping("/tabs")
    public GoogleSheetsTabsResponse listTabs(@RequestParam("spreadsheetUrl") String spreadsheetUrl) {
        return sheetsReader.listTabs(spreadsheetUrl);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse preview(
            @RequestParam("spreadsheetUrl") String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body) {
        ExcelImportResult imported = sheetsReader.readSelectedTabs(spreadsheetUrl, sheetNames);
        return campaignService.previewImported(imported, subject, body);
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftResponse createDrafts(
            @RequestParam("spreadsheetUrl") String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(name = "resume", required = false) MultipartFile resume) {
        ExcelImportResult imported = sheetsReader.readSelectedTabs(spreadsheetUrl, sheetNames);
        return campaignService.createDraftsImported(imported, resume, subject, body);
    }


    @PostMapping(
            value = "/send",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public SendResponse sendEmails(
            @RequestParam("spreadsheetUrl") String spreadsheetUrl,
            @RequestParam("sheetNames") List<String> sheetNames,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(name = "resume", required = false)
            MultipartFile resume) {

        ExcelImportResult imported =
                sheetsReader.readSelectedTabs(spreadsheetUrl, sheetNames);

        return campaignService.sendEmailsImported(
                imported, resume, subject, body
        );
    }

}
