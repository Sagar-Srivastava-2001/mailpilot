package com.sagar.emailsender.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.sagar.emailsender.gmail.GmailOAuthService;
import com.sagar.emailsender.model.DraftItemResult;
import com.sagar.emailsender.model.DraftResponse;
import com.sagar.emailsender.model.EmailContact;
import com.sagar.emailsender.model.ExcelImportResult;
import com.sagar.emailsender.model.ImportIssue;
import com.sagar.emailsender.model.PreviewResponse;
import com.sagar.emailsender.model.RecipientPreview;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.google.api.services.gmail.model.Message;
import com.sagar.emailsender.model.SendResponse;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Service
public class EmailCampaignService {
    private final ExcelContactReader contactReader;
    private final EmailTemplateService templateService;
    private final GmailOAuthService gmailOAuthService;
    private final String fromAddress;
    private final Resource bundledResume;

    @Value("${app.email.batch-limit:100}")
    private int batchLimit;

    @Value("${app.email.delay-ms:300}")
    private long delayMs;

    public EmailCampaignService(
            ExcelContactReader contactReader,
            EmailTemplateService templateService,
            GmailOAuthService gmailOAuthService,
            @Value("${app.gmail.from-address:}") String fromAddress,
            @Value("classpath:resume/Sagar_Srivastava_Resume.pdf") Resource bundledResume) {
        this.contactReader = contactReader;
        this.templateService = templateService;
        this.gmailOAuthService = gmailOAuthService;
        this.fromAddress = fromAddress;
        this.bundledResume = bundledResume;
    }

    public PreviewResponse preview(MultipartFile file, String subjectTemplate, String bodyTemplate) {
        return previewImported(contactReader.read(file), subjectTemplate, bodyTemplate);
    }


    public PreviewResponse previewImported(
            ExcelImportResult imported,
            String subjectTemplate,
            String bodyTemplate
    ) {
        validateTemplate(subjectTemplate, bodyTemplate);

        List<RecipientPreview> recipients = imported.contacts().stream()
                .map(contact -> new RecipientPreview(
                        contact.rowNumber(),
                        contact.name(),
                        contact.email(),
                        contact.company(),
                        contact.sourceSheet(),
                        templateService.render(subjectTemplate, contact),
                        templateService.render(bodyTemplate, contact)
                ))
                .toList();

        String sampleTo = "";
        String sampleSubject = "";
        String sampleBody = "";

        if (!imported.contacts().isEmpty()) {
            EmailContact first = imported.contacts().get(0);
            sampleTo = first.email();
            sampleSubject = templateService.render(subjectTemplate, first);
            sampleBody = templateService.render(bodyTemplate, first);
        }

        return new PreviewResponse(
                imported.contacts().size(),
                imported.issues().size(),
                batchLimit,
                delayMs,
                recipients,
                imported.issues(),
                sampleTo,
                sampleSubject,
                sampleBody
        );
    }

    public DraftResponse createDrafts(MultipartFile file, MultipartFile resume, String subjectTemplate, String bodyTemplate) {
        return createDraftsImported(contactReader.read(file), resume, subjectTemplate, bodyTemplate);
    }

    public DraftResponse createDraftsImported(ExcelImportResult imported, MultipartFile resume, String subjectTemplate, String bodyTemplate) {
        validateTemplate(subjectTemplate, bodyTemplate);
        validateResume(resume);
        validateImport(imported);

        if (!gmailOAuthService.isConnected()) {
            throw new IllegalStateException("Gmail is not connected. Click Connect Gmail and finish Google authorization before creating drafts.");
        }

        byte[] attachmentBytes;
        String attachmentFilename;
        if (resume != null && !resume.isEmpty()) {
            try {
                attachmentBytes = resume.getBytes();
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read the selected resume attachment.", exception);
            }
            attachmentFilename = safeAttachmentFilename(resume.getOriginalFilename());
        } else {
            attachmentBytes = readBundledResume();
            attachmentFilename = "Sagar_Srivastava_Resume.pdf";
        }
        validatePdfBytes(attachmentBytes);

        String senderAddress = StringUtils.hasText(fromAddress) ? fromAddress : gmailOAuthService.getAuthorizedEmail();
        if (!StringUtils.hasText(senderAddress)) {
            throw new IllegalStateException("Could not detect the authorized Gmail address. Set GMAIL_FROM_ADDRESS to the same address as your connected Gmail account, then restart the app.");
        }

        final Gmail gmail;
        try {
            gmail = gmailOAuthService.gmailClient();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the Gmail API client. Reconnect Gmail and try again.", exception);
        }

        List<DraftItemResult> results = new ArrayList<>();
        int created = 0;
        int failed = 0;
        for (int i = 0; i < imported.contacts().size(); i++) {
            EmailContact contact = imported.contacts().get(i);
            try {
                MimeMessage mimeMessage = buildMimeMessage(contact, subjectTemplate, bodyTemplate,
                        attachmentBytes, attachmentFilename, senderAddress);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                mimeMessage.writeTo(output);
                String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());

                Draft draft = gmail.users().drafts().create("me", new Draft()
                        .setMessage(new Message().setRaw(raw)))
                        .execute();
                created++;
                results.add(new DraftItemResult(contact.name(), contact.email(), "CREATED",
                        "Saved in Gmail Drafts. Review it in Gmail before sending.", draft.getId()));
            } catch (Exception exception) {
                failed++;
                String detail = exception.getMessage();
                if (!StringUtils.hasText(detail)) {
                    detail = exception.getClass().getSimpleName();
                }
                results.add(new DraftItemResult(contact.name(), contact.email(), "FAILED", abbreviate(detail, 220), ""));
            }

            if (i < imported.contacts().size() - 1 && delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Draft creation was interrupted. Review Gmail Drafts before retrying to avoid duplicates.", exception);
                }
            }
        }
        return new DraftResponse(imported.contacts().size(), created, failed, results);
    }

    private MimeMessage buildMimeMessage(EmailContact contact, String subjectTemplate, String bodyTemplate,
                                         byte[] attachmentBytes, String attachmentFilename,
                                         String senderAddress) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(senderAddress);
        helper.setTo(contact.email());
        helper.setSubject(templateService.render(subjectTemplate, contact));
        helper.setText(templateService.render(bodyTemplate, contact), false);
        helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes), "application/pdf");
        message.saveChanges();
        return message;
    }

    private void validateImport(ExcelImportResult imported) {
        if (!imported.issues().isEmpty()) {
            String rows = imported.issues().stream()
                    .map(issue -> (StringUtils.hasText(issue.sourceSheet()) ? "sheet '" + issue.sourceSheet() + "', " : "")
                            + "row " + issue.rowNumber() + ": " + issue.reason())
                    .limit(5)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("Fix the Excel validation issues.");
            throw new IllegalArgumentException("Draft creation is blocked until the source data issues are fixed. " + rows);
        }
        if (imported.contacts().isEmpty()) {
            throw new IllegalArgumentException("No valid recipients were found.");
        }
        if (imported.contacts().size() > batchLimit) {
            throw new IllegalArgumentException("This batch has " + imported.contacts().size()
                    + " recipients, but the configured limit is " + batchLimit
                    + ". Select fewer tabs / split the source into smaller batches, or change EMAIL_BATCH_LIMIT.");
        }
    }

    private void validateResume(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            return;
        }
        if (resume.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("Resume attachment must be 10 MB or smaller.");
        }
        String filename = resume.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Resume attachment must be a PDF file (.pdf).");
        }
        try {
            validatePdfBytes(resume.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the resume attachment.", exception);
        }
    }

    private byte[] readBundledResume() {
        try (var inputStream = bundledResume.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            validatePdfBytes(bytes);
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("The bundled resume PDF could not be loaded from the application resources.", exception);
        }
    }

    private void validatePdfBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new IllegalArgumentException("The selected resume does not appear to be a valid PDF file.");
        }
    }

    private String safeAttachmentFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "resume.pdf";
        }
        String cleaned = org.springframework.util.StringUtils.cleanPath(filename).replaceAll("[\\/\r\n]", "_");
        return cleaned.toLowerCase(Locale.ROOT).endsWith(".pdf") ? cleaned : "resume.pdf";
    }

    private void validateTemplate(String subject, String body) {
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("Email subject is required.");
        }
        if (subject.length() > 250) {
            throw new IllegalArgumentException("Email subject must be 250 characters or fewer.");
        }
        if (!StringUtils.hasText(body)) {
            throw new IllegalArgumentException("Email body is required.");
        }
        if (body.length() > 10000) {
            throw new IllegalArgumentException("Email body must be 10,000 characters or fewer.");
        }
    }

    private String abbreviate(String value, int maxLength) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }


    public SendResponse sendEmails(
            MultipartFile file,
            MultipartFile resume,
            String subjectTemplate,
            String bodyTemplate) {

        return sendEmailsImported(
                contactReader.read(file),
                resume,
                subjectTemplate,
                bodyTemplate
        );
    }

    public SendResponse sendEmailsImported(
            ExcelImportResult imported,
            MultipartFile resume,
            String subjectTemplate,
            String bodyTemplate) {

        validateTemplate(subjectTemplate, bodyTemplate);
        validateResume(resume);
        validateImport(imported);

        if (!gmailOAuthService.isConnected()) {
            throw new IllegalStateException(
                    "Gmail is not connected. Connect Gmail before sending."
            );
        }

        byte[] attachmentBytes;
        String attachmentFilename;

        if (resume != null && !resume.isEmpty()) {
            try {
                attachmentBytes = resume.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Could not read the resume attachment.", e
                );
            }
            attachmentFilename =
                    safeAttachmentFilename(resume.getOriginalFilename());
        } else {
            attachmentBytes = readBundledResume();
            attachmentFilename = "Sagar_Srivastava_Resume.pdf";
        }

        validatePdfBytes(attachmentBytes);

        String senderAddress = StringUtils.hasText(fromAddress)
                ? fromAddress
                : gmailOAuthService.getAuthorizedEmail();

        if (!StringUtils.hasText(senderAddress)) {
            throw new IllegalStateException(
                    "Could not determine the connected Gmail address."
            );
        }

        final Gmail gmail;
        try {
            gmail = gmailOAuthService.gmailClient();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize Gmail. Reconnect and try again.", e
            );
        }

        List<SendResponse.SendItemResult> results = new ArrayList<>();
        int sent = 0;
        int failed = 0;

        for (int i = 0; i < imported.contacts().size(); i++) {
            EmailContact contact = imported.contacts().get(i);

            try {
                MimeMessage mimeMessage = buildMimeMessage(
                        contact,
                        subjectTemplate,
                        bodyTemplate,
                        attachmentBytes,
                        attachmentFilename,
                        senderAddress
                );

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                mimeMessage.writeTo(output);

                String raw = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(output.toByteArray());

                Message response = gmail.users()
                        .messages()
                        .send("me", new Message().setRaw(raw))
                        .execute();

                sent++;

                results.add(new SendResponse.SendItemResult(
                        contact.name(),
                        contact.email(),
                        "SENT",
                        "Gmail accepted the message for sending.",
                        response.getId()
                ));

            } catch (Exception e) {
                failed++;

                String detail = e.getMessage();
                if (!StringUtils.hasText(detail)) {
                    detail = e.getClass().getSimpleName();
                }

                results.add(new SendResponse.SendItemResult(
                        contact.name(),
                        contact.email(),
                        "FAILED",
                        abbreviate(detail, 220),
                        ""
                ));
            }

            if (i < imported.contacts().size() - 1 && delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Sending was interrupted. Check Gmail Sent before retrying.",
                            e
                    );
                }
            }
        }

        return new SendResponse(
                imported.contacts().size(),
                sent,
                failed,
                results
        );
    }
}
