package com.sagar.emailsender.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
public class GmailOAuthService {
    private static final String LOCAL_USER_KEY = "gmail-user";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final Path clientCredentialsPath;
    private final Path tokenDirectory;
    private final Path accountEmailPath;
    private final String redirectUri;
    private final String applicationName;

    public GmailOAuthService(
            @Value("${app.gmail.credentials-file:./credentials.json}") String clientCredentialsPath,
            @Value("${app.gmail.token-dir:${user.home}/.email-excel-sender/tokens}") String tokenDirectory,
            @Value("${app.gmail.redirect-uri:http://localhost:8080/oauth2/callback}") String redirectUri,
            @Value("${spring.application.name:email-excel-sender}") String applicationName) {
        this.clientCredentialsPath = Path.of(clientCredentialsPath).toAbsolutePath().normalize();
        this.tokenDirectory = Path.of(tokenDirectory).toAbsolutePath().normalize();
        this.accountEmailPath = this.tokenDirectory.resolve("gmail-account.txt");
        this.redirectUri = redirectUri;
        this.applicationName = applicationName;
    }

    public boolean isClientCredentialsConfigured() {
        return Files.isRegularFile(clientCredentialsPath);
    }

    public boolean isConnected() {
        if (!isClientCredentialsConfigured()) {
            return false;
        }
        try {
            Credential credential = newFlow().loadCredential(LOCAL_USER_KEY);
            return credential != null
                    && (hasText(credential.getRefreshToken()) || hasText(credential.getAccessToken()));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public String authorizationUrl(String state) throws IOException {
        ensureClientCredentialsExist();
        return newFlow().newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                .set("prompt", "consent")
                .setState(state)
                .build();
    }

    public void completeAuthorization(String authorizationCode) throws IOException {
        ensureClientCredentialsExist();
        GoogleAuthorizationCodeFlow flow = newFlow();
        GoogleTokenResponse tokenResponse = flow.newTokenRequest(authorizationCode)
                .setRedirectUri(redirectUri)
                .execute();
        flow.createAndStoreCredential(tokenResponse, LOCAL_USER_KEY);
        String verifiedEmail = verifiedEmail(tokenResponse.getIdToken());
        if (hasText(verifiedEmail)) {
            Files.createDirectories(tokenDirectory);
            Files.writeString(accountEmailPath, verifiedEmail, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public Gmail gmailClient() throws IOException {
        ensureClientCredentialsExist();
        GoogleAuthorizationCodeFlow flow = newFlow();
        Credential credential = flow.loadCredential(LOCAL_USER_KEY);
        if (credential == null || (!hasText(credential.getRefreshToken()) && !hasText(credential.getAccessToken()))) {
            throw new IllegalStateException("Gmail is not connected. Click Connect Gmail and complete Google authorization first.");
        }

        NetHttpTransport transport = newTransport();
        return new Gmail.Builder(transport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    public Sheets sheetsClient() throws IOException {
        ensureClientCredentialsExist();
        GoogleAuthorizationCodeFlow flow = newFlow();
        Credential credential = flow.loadCredential(LOCAL_USER_KEY);
        if (credential == null || (!hasText(credential.getRefreshToken()) && !hasText(credential.getAccessToken()))) {
            throw new IllegalStateException("Google is not connected. Click Connect Gmail and finish Google authorization before reading Sheets.");
        }

        return new Sheets.Builder(newTransport(), JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    public String getAuthorizedEmail() {
        if (Files.isRegularFile(accountEmailPath)) {
            try {
                return Files.readString(accountEmailPath).trim();
            } catch (IOException ignored) {
                return "";
            }
        }
        return "";
    }

    public String getConnectionMessage() {
        if (!isClientCredentialsConfigured()) {
            return "Add your Google OAuth client JSON as credentials.json in the project directory, then restart the app.";
        }
        if (!isConnected()) {
            return "Connect Google to read selected Sheets tabs and create Gmail drafts. No messages will be sent by this app.";
        }
        return "Google is connected. The app can read Sheets and create Gmail drafts; it does not send messages.";
    }

    private GoogleAuthorizationCodeFlow newFlow() throws IOException {
        ensureClientCredentialsExist();
        GoogleClientSecrets clientSecrets;
        try (Reader reader = Files.newBufferedReader(clientCredentialsPath)) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        }
        Files.createDirectories(tokenDirectory);
        return new GoogleAuthorizationCodeFlow.Builder(
                newTransport(),
                JSON_FACTORY,
                clientSecrets,
                List.of(GmailScopes.GMAIL_COMPOSE, SheetsScopes.SPREADSHEETS_READONLY, "openid", "email"))
                .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory.toFile()))
                .setAccessType("offline")
                .build();
    }

    private String verifiedEmail(String idToken) {
        if (!hasText(idToken)) {
            return "";
        }
        try {
            GoogleClientSecrets secrets;
            try (Reader reader = Files.newBufferedReader(clientCredentialsPath)) {
                secrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
            }
            String clientId = secrets.getDetails().getClientId();
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(newTransport(), JSON_FACTORY)
                    .setAudience(List.of(clientId))
                    .build();
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
                return "";
            }
            return token.getPayload().getEmail();
        } catch (Exception ignored) {
            // OAuth is already complete; the optional From header can be supplied via GMAIL_FROM_ADDRESS.
            return "";
        }
    }

    private NetHttpTransport newTransport() throws IOException {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException exception) {
            throw new IOException("Could not initialize secure HTTPS for the Gmail API.", exception);
        }
    }

    private void ensureClientCredentialsExist() {
        if (!isClientCredentialsConfigured()) {
            throw new IllegalStateException("Google OAuth credentials were not found at " + clientCredentialsPath
                    + ". Follow the Gmail setup steps in README.md.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
