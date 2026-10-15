package com.sagar.emailsender.api;

import com.sagar.emailsender.model.ApiError;
import com.sagar.emailsender.model.DraftResponse;
import com.sagar.emailsender.model.PreviewResponse;
import com.sagar.emailsender.service.EmailCampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartFile;
import com.sagar.emailsender.model.SendResponse;

@RestController
@RequestMapping("/api/email-campaigns")
public class EmailCampaignController {
    private final EmailCampaignService campaignService;

    public EmailCampaignController(EmailCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body) {
        return campaignService.preview(file, subject, body);
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftResponse createDrafts(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(name = "resume", required = false) MultipartFile resume) {
        return campaignService.createDrafts(file, resume, subject, body);
    }


    @PostMapping(
            value = "/send",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public SendResponse sendEmails(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(name = "resume", required = false)
            MultipartFile resume) {

        return campaignService.sendEmails(
                file, resume, subject, body
        );
    }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(exception.getMessage()));
    }
}
