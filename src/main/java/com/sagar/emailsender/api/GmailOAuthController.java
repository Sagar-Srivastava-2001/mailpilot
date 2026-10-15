package com.sagar.emailsender.api;

import com.sagar.emailsender.gmail.GmailOAuthService;
import com.sagar.emailsender.model.GmailStatusResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Controller
public class GmailOAuthController {
    private static final String STATE_SESSION_KEY = "gmail_oauth_state";
    private final GmailOAuthService gmailOAuthService;

    public GmailOAuthController(GmailOAuthService gmailOAuthService) {
        this.gmailOAuthService = gmailOAuthService;
    }

    @GetMapping("/api/gmail/status")
    @ResponseBody
    public GmailStatusResponse status() {
        boolean configured = gmailOAuthService.isClientCredentialsConfigured();
        boolean connected = gmailOAuthService.isConnected();
        return new GmailStatusResponse(configured, connected, gmailOAuthService.getConnectionMessage());
    }

    @GetMapping("/api/gmail/connect")
    public RedirectView connect(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE_SESSION_KEY, state);
        try {
            return new RedirectView(gmailOAuthService.authorizationUrl(state));
        } catch (IOException | IllegalStateException exception) {
            session.removeAttribute(STATE_SESSION_KEY);
            return redirectWithMessage("gmailError", "Could not start Gmail authorization. Check credentials.json and README.md.");
        }
    }

    @GetMapping("/oauth2/callback")
    public RedirectView callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String oauthError,
            HttpSession session) {
        Object expectedState = session.getAttribute(STATE_SESSION_KEY);
        session.removeAttribute(STATE_SESSION_KEY);
        if (oauthError != null) {
            return redirectWithMessage("gmailError", "Google authorization was cancelled or denied.");
        }
        if (code == null || code.isBlank() || expectedState == null || !Objects.equals(expectedState, state)) {
            return redirectWithMessage("gmailError", "Gmail authorization could not be verified. Try Connect Gmail again.");
        }
        try {
            gmailOAuthService.completeAuthorization(code);
            return new RedirectView("/?gmailConnected=true");
        } catch (IOException | RuntimeException exception) {
            return redirectWithMessage("gmailError", "Gmail authorization failed. Check the redirect URI and OAuth client configuration, then try again.");
        }
    }

    private RedirectView redirectWithMessage(String parameter, String message) {
        return new RedirectView("/?" + parameter + "=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
