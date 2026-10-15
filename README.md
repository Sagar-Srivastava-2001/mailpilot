# Gmail Draft Creator

A local Spring Boot application that creates personalized, **unsent Gmail drafts** from either an Excel workbook or selected tabs in a Google Sheets spreadsheet. Each valid contact gets a separate Gmail draft with your chosen subject, personalized message, and a PDF resume attachment.

> [!IMPORTANT]
> **This application creates Gmail drafts only.** It does not automatically send or schedule emails from the application. To schedule delivery, open a draft in Gmail, click the arrow next to **Send**, choose **Schedule send**, and select your preferred date and time. Gmail manages delivery after scheduling.
>
> This copy of the project is prefilled with Sagar Srivastava's example email template and resume. Replace the example template with your own details and attach your own resume before creating drafts. Do not distribute the bundled resume to others without permission.

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [1. Create a Google Cloud project](#1-create-a-google-cloud-project)
- [2. Enable the APIs](#2-enable-the-apis)
- [3. Configure the OAuth consent screen](#3-configure-the-oauth-consent-screen)
- [4. Add OAuth scopes](#4-add-oauth-scopes)
- [5. Create a Web application OAuth client](#5-create-a-web-application-oauth-client)
- [6. Download credentials.json](#6-download-credentialsjson)
- [7. Run the application](#7-run-the-application)
- [8. Connect Gmail and create drafts](#8-connect-gmail-and-create-drafts)
- [Contact data format](#contact-data-format)
- [Customize the message and resume](#customize-the-message-and-resume)
- [Configuration](#configuration)
- [Security and privacy](#security-and-privacy)
- [Troubleshooting](#troubleshooting)
- [API endpoints](#api-endpoints)
- [How Google OAuth testing works](#how-google-oauth-testing-works)
- [Useful Google documentation](#useful-google-documentation)

## Features

- Creates one individual Gmail draft for each contact.
- Reads contacts from `.xlsx` / `.xls` files or directly from one or more Google Sheets tabs.
- Personalizes subject and body using `{{name}}`, `{{company}}`, and `{{email}}`.
- Attaches a PDF resume to each draft.
- Previews the personalized subject and body for each contact using a contact selector in the large preview panel.
- Supports Gmail's native **Schedule send** workflow: create drafts in the app, then schedule delivery individually in Gmail.
- Validates required fields, basic email format, and duplicate addresses.
- Shows per-recipient draft results and Gmail draft IDs.
- Keeps OAuth tokens in a local folder rather than in the source directory.
- Does not use SMTP, send messages, or persist contact lists in a database.

## Requirements

- **Java JDK 21**. Confirm with `java -version`.
- Internet access for downloading Maven and project dependencies the first time.
- A Google account with Gmail.
- A Google Cloud project where you can enable APIs and create an OAuth client.
- A contact workbook or Google Sheet containing `Name`, `Email Address`, and `Company` columns.

The app is intended to run on your own computer. The included `mvnw` launcher works on macOS/Linux. On Windows, install Maven and use `mvn spring-boot:run` from PowerShell or Command Prompt.

## 1. Create a Google Cloud project

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Select an existing project or click **New project**.
3. Give the project a name, such as `Gmail Draft Creator`, and create it.
4. Confirm the new project is selected in the project picker at the top of the console.

Use this same Google Cloud project for enabling APIs, configuring OAuth, and creating the OAuth client.

## 2. Enable the APIs

Enable both APIs in the project you selected above:

- [Enable Gmail API](https://console.cloud.google.com/apis/library/gmail.googleapis.com)
- [Enable Google Sheets API](https://console.cloud.google.com/apis/library/sheets.googleapis.com)

Open each page, check the selected project, and click **Enable**. If the page shows **Manage**, the API is already enabled.

If Gmail draft creation later fails with `403 SERVICE_DISABLED`, the Gmail API is usually disabled in the project associated with the OAuth credentials. Check the `project_id` in `credentials.json` and enable Gmail API in that exact project.

## 3. Configure the OAuth consent screen

Open [Google Auth Platform](https://console.cloud.google.com/auth/overview). If the platform has not been configured, click **Get started**.

### Branding

Enter an app name, for example `Gmail Draft Creator`, and provide a support email and developer contact email that you control.

### Audience

Choose the audience that matches how the app will be used:

- **External**: use this for a personal Gmail account or users outside your Google Workspace organization.
- **Internal**: use this only if the app is restricted to accounts in your own Google Workspace organization and your organization offers this option.

For an External app used personally, leave the publishing status as **Testing** and add every Google account that will authorize the app under **Test users → Add users**. Add the exact Gmail account you will select in the browser during authorization.

For an app in Testing mode, only listed test users can authorize it. Google currently limits a Testing app to 100 test users, and refresh tokens for apps requesting scopes beyond basic identity generally expire after seven days. See [How Google OAuth testing works](#how-google-oauth-testing-works).

## 4. Add OAuth scopes

In Google Auth Platform, open **Data Access → Add or remove scopes** and add the following scopes:

```text
https://www.googleapis.com/auth/gmail.compose
https://www.googleapis.com/auth/spreadsheets.readonly
openid
email
```

What they do:

| Scope | Purpose |
|---|---|
| `gmail.compose` | Lets the app create and manage Gmail drafts. Google classifies this scope as restricted and its permission also allows sending mail. This code uses it only to create drafts. |
| `spreadsheets.readonly` | Reads spreadsheet tabs and values without modifying the spreadsheet. |
| `openid` and `email` | Allow the app to identify the account that authorized the connection. |

Save the scope changes. Google may display an unverified-app or testing warning during authorization. Only authorize an OAuth client you created and a copy of the source code you trust.

**Public distribution note:** This repository is designed for personal/local use. If you intend to offer a single shared app to arbitrary Google users, review Google's current verification requirements for `gmail.compose`. Publishing a public app that requests restricted scopes can require OAuth verification and may involve additional review or security assessment. Keeping a private app in Testing mode is generally simpler for personal use.

## 5. Create a Web application OAuth client

1. Open [Google Auth Platform → Clients](https://console.cloud.google.com/auth/clients).
2. Click **Create client** (or **Create OAuth client**).
3. Set **Application type** to **Web application**.
4. Name it something like `Gmail Draft Creator - Local`.
5. Under **Authorized redirect URIs**, click **Add URI** and enter this exact value:

   ```text
   http://localhost:8080/oauth2/callback
   ```

6. Leave **Authorized JavaScript origins** blank; this app uses a server-side OAuth callback.
7. Click **Create**.

The redirect URI must match exactly, including protocol, port, and path. If you change the app port or redirect URI, update both Google Cloud and the app configuration.

## 6. Download `credentials.json`

1. Open the OAuth client you just created in Google Auth Platform → Clients.
2. Download its JSON file.
3. Place it in the project root, in the same directory as `pom.xml`, and name it `credentials.json`.

Expected folder structure:

```text
gmail-draft-creator/
├── credentials.json       # Your private OAuth client file; do not commit
├── pom.xml
├── mvnw
├── sample-contacts.xlsx
└── src/
```

The JSON should contain a `web` client entry because you created a Web application OAuth client. Keep this file private. It is ignored by the provided `.gitignore`.

If you prefer to store the JSON elsewhere, set `GOOGLE_OAUTH_CREDENTIALS` to its absolute path. See [Configuration](#configuration).

## 7. Run the application

Open a terminal in the project root.

### macOS / Linux

```bash
java -version
chmod +x mvnw
./mvnw spring-boot:run
```

### Windows

Install Maven and Java 21, then run from PowerShell or Command Prompt:

```powershell
java -version
mvn spring-boot:run
```

On the first run, Maven downloads project dependencies, so an internet connection is required. Keep the terminal open while using the app.

Open [http://localhost:8080](http://localhost:8080) in your browser.

## 8. Connect Gmail and create drafts

1. Click **Connect Gmail**.
2. Sign in with the account you added as a test user, if the app is in Testing mode.
3. Review the permissions and approve access.
4. Choose **Excel workbook** or **Google Sheets**.
5. Load the contacts and click **Preview contacts & message**.
6. Review the recipient list and validation issues. Use the contact selector in the large **First personalized draft** preview to inspect each recipient's personalized email.
7. Select the correct PDF in **Resume attachment**.
8. Click **Create Gmail drafts** and confirm.
9. Open Gmail's Drafts folder and review the messages and attachments.
10. To schedule an email, open its draft, click the arrow next to **Send**, choose **Schedule send**, and select the desired date and time. Repeat for each email you want to schedule.

A result with status `CREATED` means Gmail saved an unsent draft. It does **not** mean the email was sent or scheduled. Scheduling must be completed separately in Gmail for each message. After scheduling, verify the message appears in Gmail's **Scheduled** folder. If some requests fail, check Gmail Drafts before retrying; otherwise you may create duplicate drafts for recipients that already succeeded.

## Contact data format

### Excel workbook

- Supported extensions: `.xlsx` and `.xls`.
- The app reads the **first worksheet only**.
- The first row of that worksheet must contain these headers:

  | Name | Email Address | Company |
  |---|---|---|
  | Taylor Morgan | taylor@example.com | Example Company |

- Header capitalization, spaces, and punctuation are ignored.
- All three fields are required.
- Rows with malformed email addresses or duplicate email addresses are flagged.
- Fix every reported issue before creating drafts; draft creation is blocked if the import has issues.

### Google Sheets

1. Choose **Google Sheets** in the app.
2. Paste the spreadsheet URL, such as `https://docs.google.com/spreadsheets/d/SPREADSHEET_ID/edit`.
3. Click **Load tabs**.
4. Select one or more tabs and preview the contacts.

Each selected tab must have headers in its first row. Accepted headers are `Name`, `Email Address` (or `Email`), and `Company`; capitalization and punctuation are ignored. Blank rows are skipped. Duplicate email addresses across selected tabs are flagged. The app only reads the selected tabs; it does not modify the spreadsheet.

The spreadsheet must be accessible to the same Google account you connected in the app. If another account owns the sheet, share it with the connected account, or reconnect using an account that already has access.

## Customize the message and resume

### Subject and body

The subject and body are editable directly in the browser UI. The default values in this copy are an example and include Sagar's name, experience, and accomplishments. Replace those details with your own before creating drafts.

Supported placeholders are case-sensitive:

- `{{name}}` — contact's name.
- `{{company}}` — contact's company.
- `{{email}}` — contact's email address.

Placeholders can be used in the subject or body. Use the contact selector in the large **First personalized draft** preview to check each recipient's rendered subject and body before creating drafts.

### Resume attachment

This project currently bundles `Sagar_Srivastava_Resume.pdf` in `src/main/resources/resume/`. You have two options:

1. **Recommended for quick use:** select your own PDF in the UI's **Resume attachment** field before creating a batch. It overrides the bundled resume for that batch. The file must be a PDF and no larger than 10 MB.
2. **Replace the default permanently:** replace `src/main/resources/resume/Sagar_Srivastava_Resume.pdf` with your own PDF, keeping that filename. If you build a packaged JAR, rebuild it after replacing the file.

Do not create drafts until you have confirmed the correct resume is attached.

## Configuration

These environment variables are optional. Defaults are shown:

| Variable | Default | Purpose |
|---|---|---|
| `GOOGLE_OAUTH_CREDENTIALS` | `./credentials.json` | Path to the downloaded Google OAuth client JSON. |
| `GOOGLE_OAUTH_REDIRECT_URI` | `http://localhost:8080/oauth2/callback` | Must exactly match the authorized redirect URI in Google Cloud. |
| `GMAIL_TOKEN_DIR` | `~/.email-excel-sender/tokens` | Local directory used to store the OAuth token and connected-account information. |
| `GMAIL_FROM_ADDRESS` | Auto-detected from the connected account | Set this only if the app cannot detect the authorized Gmail address. Use the same mailbox address or a configured Gmail send-as alias. |
| `EMAIL_BATCH_LIMIT` | `100` | Maximum number of contacts allowed in one batch. Larger batches are blocked and must be split. |
| `EMAIL_DELAY_MS` | `300` | Delay between Gmail API draft-creation requests, in milliseconds. |

Example on macOS/Linux:

```bash
export EMAIL_BATCH_LIMIT=100
export EMAIL_DELAY_MS=300
# Optional: provide a different path to the OAuth client JSON.
# export GOOGLE_OAUTH_CREDENTIALS="$HOME/secrets/credentials.json"
./mvnw spring-boot:run
```

Example in PowerShell:

```powershell
$env:EMAIL_BATCH_LIMIT = "100"
$env:EMAIL_DELAY_MS = "300"
# Optional:
# $env:GOOGLE_OAUTH_CREDENTIALS = "$HOME\secrets\credentials.json"
mvn spring-boot:run
```

The 100-contact cap and delay are application safeguards, not Gmail quota guarantees. Google API quotas and policies may change; check the current Google Cloud quota pages if you increase the batch size.

## Security and privacy

- Run this app on your own computer and keep it bound to a trusted local environment. **Do not expose it to the public internet.** It has no application login, and a connected OAuth token allows access to the connected Google APIs.
- Do not commit or share `credentials.json`, token files, or real contact spreadsheets.
- The default token directory is `~/.email-excel-sender/tokens`. Treat it as sensitive.
- The application does not store contact lists or campaign history in a database. It holds imported contact data in memory while handling a request; created drafts are stored by Gmail.
- The app uses no SMTP credentials and has no code path to send messages. Google still grants the `gmail.compose` scope, which is capable of sending messages; the app code uses this permission only to create drafts.
- To disconnect, revoke the application's access under [Google Account connections](https://myaccount.google.com/connections) and remove the local token directory.

macOS/Linux:

```bash
rm -rf "$HOME/.email-excel-sender/tokens"
```

PowerShell:

```powershell
Remove-Item -Recurse -Force "$HOME\.email-excel-sender\tokens"
```

If you set `GMAIL_TOKEN_DIR`, delete the configured directory instead.

## Troubleshooting

### `403 SERVICE_DISABLED` when creating Gmail drafts

1. Open [Gmail API](https://console.cloud.google.com/apis/library/gmail.googleapis.com).
2. Select the project associated with `credentials.json` and click **Enable** (or confirm it says **Manage**).
3. Wait a few minutes and retry.

To identify the project, open `credentials.json` and check its `project_id` field. Enabling the API in a different project will not fix the error.

### `403 access_denied` / the app has not completed verification

If the OAuth app is External and in Testing mode, open [Google Auth Platform → Audience](https://console.cloud.google.com/auth/audience), add the exact account you'll use under **Test users**, and save. Select that same account in the OAuth consent prompt. Personal/local testing typically does not require public verification, but only authorized test users can access an app in Testing mode.

### Google Sheets returns `403` or the tabs don't load

- Enable the [Google Sheets API](https://console.cloud.google.com/apis/library/sheets.googleapis.com) in the same Cloud project.
- Confirm `https://www.googleapis.com/auth/spreadsheets.readonly` is present under **Data Access**.
- Confirm the connected Google account has access to the spreadsheet.
- Re-authorize after changing scopes as described below.

### A newly added scope is ignored or Google still denies access

Stop the app, remove the saved OAuth token directory, restart the app, and click **Connect Gmail** again. This forces Google to request consent with the updated scopes.

macOS/Linux:

```bash
rm -rf "$HOME/.email-excel-sender/tokens"
```

PowerShell:

```powershell
Remove-Item -Recurse -Force "$HOME\.email-excel-sender\tokens"
```

If `GMAIL_TOKEN_DIR` is configured, remove that directory instead. You will need to authorize the app again.

### `redirect_uri_mismatch`

Make sure the authorized redirect URI in Google Cloud is exactly:

```text
http://localhost:8080/oauth2/callback
```

Also make sure `GOOGLE_OAUTH_REDIRECT_URI` has the same value and that the app is running on port 8080.

### Google authorization needs to be repeated after several days

For an External OAuth app in Testing mode, refresh tokens generally expire after seven days when the app requests scopes beyond basic identity. Reconnect Gmail when prompted, or review Google's current OAuth publishing rules if you need persistent access beyond testing.

### The app cannot detect the Gmail sender address

Set `GMAIL_FROM_ADDRESS` to the same address as the connected Gmail account, restart the app, and reconnect if needed. Only use an address configured as a valid send-as identity for that mailbox.

### Draft creation partially fails

Read the per-recipient details in the result table. Fix the cause before retrying. Check Gmail Drafts first, because successful drafts from a partially failed batch remain in Gmail and could be duplicated if you retry the whole batch.

## API endpoints

These endpoints are for local development and are not protected by an application login. Do not expose them publicly.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/gmail/status` | Check whether OAuth credentials exist and Gmail is connected. |
| `GET` | `/api/gmail/connect` | Start Google OAuth authorization. |
| `GET` | `/oauth2/callback` | Handle Google's local OAuth callback. |
| `POST` | `/api/email-campaigns/preview` | Validate and preview an uploaded workbook (`file`, `subject`, `body`). |
| `POST` | `/api/email-campaigns/drafts` | Create drafts from an uploaded workbook (`file`, `subject`, `body`, optional `resume`). |
| `GET` | `/api/google-sheets/tabs` | List tabs in a spreadsheet (`spreadsheetUrl`). |
| `POST` | `/api/google-sheets/preview` | Preview selected tabs (`spreadsheetUrl`, repeated `sheetNames`, `subject`, `body`). |
| `POST` | `/api/google-sheets/drafts` | Create drafts from selected tabs (`spreadsheetUrl`, repeated `sheetNames`, `subject`, `body`, optional `resume`). |

There is deliberately no `/send` or `/schedule` endpoint. The application creates drafts through Gmail's `drafts.create` operation; it does not call `drafts.send` or automatically schedule delivery. Gmail's native **Schedule send** functionality is used manually after draft creation.

## How Google OAuth testing works

For an app used only by you, the simplest setup is usually:

1. Set the user type to **External** if you're using a personal Gmail account.
2. Keep the publishing status as **Testing**.
3. Add your own Google account under **Test users**.
4. Use your own OAuth client JSON locally.

Under Google's current rules, an External app in Testing mode can have up to 100 test users and only those accounts can authorize it. Refresh tokens typically expire after seven days for these non-basic scopes. If an app is published but remains unverified while requesting sensitive or restricted scopes, a separate 100-user cap can apply to total authorizing users. The exact verification requirements depend on the app's audience, OAuth scopes, and distribution plan; consult Google's current policy before offering this as a public service.

Useful official references:

- [OAuth app publishing states](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview)
- [OAuth verification and restricted scopes](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification)
- [Gmail API scope definitions](https://developers.google.com/workspace/gmail/api/auth/scopes)

## Useful Google documentation

- [Google Cloud Console](https://console.cloud.google.com/)
- [Enable Gmail API](https://console.cloud.google.com/apis/library/gmail.googleapis.com)
- [Enable Google Sheets API](https://console.cloud.google.com/apis/library/sheets.googleapis.com)
- [Google Auth Platform — Audience](https://console.cloud.google.com/auth/audience)
- [Google Auth Platform — Data Access](https://console.cloud.google.com/auth/scopes)
- [Google Auth Platform — OAuth clients](https://console.cloud.google.com/auth/clients)
- [Gmail API: Create a draft](https://developers.google.com/workspace/gmail/api/guides/drafts)
- [Gmail Help: Schedule emails to send](https://support.google.com/mail/answer/9214606?hl=en)
- [Google Sheets API: Java quickstart](https://developers.google.com/workspace/sheets/api/quickstart/java)

## Implementation details

- Java 21 and Spring Boot 3.5
- Apache POI for `.xlsx` / `.xls` parsing
- Gmail API for draft creation
- Google Sheets API with read-only spreadsheet access
- Google OAuth 2.0 for user authorization
- Jakarta Mail MIME helpers for message content and PDF attachments
- No database, SMTP configuration, or automated send operation
