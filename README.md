# EncorePay Job Monitoring

Production-oriented Selenium/TestNG automation for the EncorePay Admin Jobs flow across multiple client environments.

## Monitored jobs

- Post Receipts Job
- Encore Download Collection Items Job
- Encore Up Coming Demands Job, when present for the client

## Post Receipts flow

The automation:

1. Opens Admin -> Job.
2. Opens Receipt from the Post Receipts Job row.
3. Opens Show Filter when the filter panel is collapsed.
4. Leaves existing Month and Year values unchanged.
5. Sets only Receipt Date to today's date.
6. Selects FAILED and searches.
7. Reads the total failed count from the paginator.
8. Opens the red `error_outline` icon on each failed row and captures unique failure reason(s).
9. Selects PENDING and searches.
10. Reads the total pending count from the paginator.
11. Closes Receipt and returns to the Jobs page.
12. Opens View for Post Receipts Job.
13. Opens the latest execution View.
14. Reads Status and End Date/Time from the execution modal.
15. Closes the execution modal and Job Details before continuing.

## Collection Item and Upcoming Demand flow

For each configured job, the automation opens View, opens the latest execution record, reads Status and End Date/Time from the execution modal, then closes the execution modal and Job Details.

## Multi-client execution

The `MultiClientAdminJobsTest` runs configured clients sequentially.

For each client:

1. A fresh browser is started.
2. The client's configured URL is opened.
3. The client is authenticated.
4. The existing job-monitoring flow is executed.
5. Logout is completed.
6. The Sign In page is confirmed.
7. The browser is closed.
8. The next configured client's URL is opened.

A failure in one client is recorded and does not stop the remaining clients.

The existing `AdminJobsTest` remains available for single-client execution.

## Client configuration

Use `src/main/resources/config.properties` for client names and URLs:

```properties
client.1.name=Prayaan Capital
client.1.url=https://uat.prayaancapital.net/encore-pay/#/signin

client.2.name=Client Two
client.2.url=https://uat.client2.example/encore-pay/#/signin

client.3.name=Client Three
client.3.url=https://uat.client3.example/encore-pay/#/signin
```

Continue through `client.9` for up to nine configured clients.

Credentials should not be committed to source control. Use environment variables:

```powershell
$env:CLIENT_1_USERNAME="senseiadmin"
$env:CLIENT_1_PASSWORD="your-password"

$env:CLIENT_2_USERNAME="senseiadmin"
$env:CLIENT_2_PASSWORD="your-password"

$env:CLIENT_3_USERNAME="senseiadmin"
$env:CLIENT_3_PASSWORD="your-password"
```

Continue the same pattern through `CLIENT_9_USERNAME` and `CLIENT_9_PASSWORD`.

The same configuration can also be supplied through `CLIENT_1_URL`, `CLIENT_1_NAME`, and so on. Environment variables take precedence over the properties file.

## Running

For all configured clients:

```powershell
.\mvnw.cmd clean test
```

For the existing single-client test class, run the class directly through your IDE/TestNG configuration or use the supplied single-client TestNG suite if needed.

## Reports

Reports are written to:

`test-output/report/`

The HTML report uses the same captured monitoring data.

Post Receipt Job:

`Client | Status | Failed | Pending to Be Posted | Date & Time | Failure Reason(s)`

Download Collection Item Job:

`Client | Status | Date & Time`

Upcoming Demand Job:

`Client | Status | Date & Time`

## Security

Do not commit real passwords, SMTP passwords, API keys, webhook secrets, `.env` files, or other credentials.

`src/main/resources/config.properties` is git-ignored and untracked. Copy `src/main/resources/config.properties.example` to `config.properties` and fill it in locally. Any value in it can be overridden by an environment variable of the same name in UPPER_SNAKE_CASE, for example `client.1.username` becomes `CLIENT_1_USERNAME`.

## Scheduled runs (GitHub Actions)

`.github/workflows/job-monitoring.yml` runs the whole suite on a schedule and needs no machine kept switched on.

| Time | Cron (UTC) | Cron expression |
|------|------------|-----------------|
| 10:00 pm IST | 16:30 UTC | `30 16 * * *` |
| 7:00 am IST  | 01:30 UTC | `30 1 * * *` |

GitHub cron is always UTC, so edit those two lines if the schedule changes. The workflow also exposes `workflow_dispatch` for a manual run from the Actions tab.

It runs on `ubuntu-latest` with JDK 17, stable Chrome, and `headless=true` forced on because a runner has no display. A cold runner is given `explicitWait=45` and `bootTimeout=150`, since the Angular bundle and its first API call take longer to arrive there than on a developer machine. The HTML report is uploaded as an artifact even when a client fails, since a failed run still produces a report, and a `Check HTML report` step fails visibly if no report was generated.

When any wait times out, the failure reports the current URL, page title, document readiness, the rendered text, the step it stopped at, and a screenshot path in `screenshots/`, instead of Selenium's default message that names only the page class.

### Reporting timezone

Job Monitoring reports in a single business timezone, `Asia/Kolkata` by default, set with `businessZone` (or `-DbusinessZone=...`).

This matters because the application renders every job timestamp with the **browser's** date formatting, not with a zone the automation supplies. A GitHub runner's browser is UTC, so the same application instant would be printed 5:30 earlier there than on a workstation in India. The workflow therefore pins the browser timezone as well as the Java-side date logic, so an identical run produces an identical report on both. No application-supplied timestamp is adjusted.

The same zone drives "business today" for the receipt date filter and the report's `Generated` stamp. Without it, a run in the UTC window between midnight and 05:30 IST would filter yesterday's receipts.

### Required repository secrets

Add these under **Settings → Secrets and variables → Actions**. At least one client URL must resolve, either through `CLIENT_URLS` or through `CLIENT_N_URL`; a client with no URL is simply not monitored.

| Secret | Purpose |
|--------|---------|
| `CLIENT_URLS` | Comma-separated client URLs, split on commas, semicolons, or new lines. Unnamed clients get a name derived from the host. |
| `CLIENT_N_URL` | Admin Jobs URL for client N. A client is monitored only when this is set. |
| `CLIENT_N_NAME` | Display name for client N. Optional. |
| `CLIENT_N_USERNAME` | Login for client N. Optional; falls back to `ADMIN_USERNAME`. |
| `CLIENT_N_PASSWORD` | Password for client N. Optional; falls back to `ADMIN_PASSWORD`. |
| `CLIENT_N_SSO` | `true` for the SSO client. Optional, defaults to `false`. |
| `GOOGLE_CHAT_WEBHOOK_URL` | Google Chat incoming webhook. |
| `GOOGLE_CHAT_OAUTH_CLIENT_ID` | OAuth client ID used to attach the HTML report to the message. Optional; without it the notification is text-only. |
| `GOOGLE_CHAT_OAUTH_CLIENT_SECRET` | OAuth client secret for the same client. Required together with the two other `GOOGLE_CHAT_OAUTH_*` values. |
| `GOOGLE_CHAT_OAUTH_REFRESH_TOKEN` | OAuth refresh token with the `https://www.googleapis.com/auth/chat.messages.create` scope. |
| `REPORT_EMAIL_ENABLED` | `true` to send email. |
| `REPORT_EMAIL_TO`, `REPORT_EMAIL_CC` | Recipients. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP settings. |

`CLIENT_N_*` runs from N=1 to N=9, matching the nine supported clients. Clients 1-9 can be configured in any mix: either list every URL in `CLIENT_URLS`, or give each monitored client its own `CLIENT_N_URL`. Credentials only need setting for the ones that differ from the shared defaults. `CLIENT_URLS` is checked first, so if it is set, the `CLIENT_N_URL` values are not read.

### Attaching the HTML report to the Google Chat message

Incoming webhooks can post **text only** — they cannot carry file attachments. To have the HTML report attached to the chat message, the run uses the Google Chat API with a user OAuth token, so all three OAuth secrets must be set:

1. In a Google Cloud project, enable the **Google Chat API**.
2. Configure the OAuth consent screen, then create an **OAuth client** (Desktop or Web app) and note the client ID and secret.
3. Authorize it with the scope `https://www.googleapis.com/auth/chat.messages.create` to obtain a **refresh token**.
4. Make sure the **user who authorized the token is a member of the target space** — the API acts on that user's behalf — and that `GOOGLE_CHAT_WEBHOOK_URL` names that same space, otherwise the attachment upload or message creation fails with a permission or not-found error.
5. Store `GOOGLE_CHAT_OAUTH_CLIENT_ID`, `GOOGLE_CHAT_OAUTH_CLIENT_SECRET` and `GOOGLE_CHAT_OAUTH_REFRESH_TOKEN` as repository secrets.

While the OAuth app is in **Testing** mode, Google expires refresh tokens after 7 days — republish the app or re-authorize when notifications mysteriously stop attaching the report. If any of the three is missing, the run logs a warning and posts a text-only notification that still links to the run's report artifact.

**If the report still does not arrive,** the run emits a `Google Chat report attachment failed` annotation next to the failing step with the underlying error:

| Error in the annotation | Cause | Fix |
|---|---|---|
| `invalid_grant` | Refresh token expired or revoked (7 days in Testing mode) | Re-authorize to get a fresh refresh token |
| `invalid_client` | Wrong client ID or secret | Check `GOOGLE_CHAT_OAUTH_CLIENT_ID` / `GOOGLE_CHAT_OAUTH_CLIENT_SECRET` |
| `403` | `chat.messages.create` scope not granted, or the authorizing user is no longer in the space | Re-consent with the scope, or restore space membership |
| `404` | The webhook URL names a different space than the OAuth credentials can reach | Point `GOOGLE_CHAT_WEBHOOK_URL` at the same space |

### Local run with no config file

Everything can come from the environment, which is how CI runs:

```powershell
$env:CLIENT_URLS = "https://client1.example/#/signin,https://client2.example/#/signin"
$env:CLIENT_1_USERNAME = "user"
$env:CLIENT_1_PASSWORD = "password"
.\mvnw.cmd clean test
```


## Frontend alignment

The monitoring locators and flow were aligned with the supplied EncorePay frontend source for Job, Post Receipts, Job Details, and execution-detail behavior.

## SSO client support

SSO is disabled by default. Normal clients require no SSO configuration. Enable it only for the client that uses SSO:

```properties
client.1.sso=false
client.2.sso=true
```

There are no SSO locators in `config.properties`. All SSO UI handling is kept inside `LoginPage`. This matches the supplied EncorePay frontend, where the login component displays the SSO button only when the configured authentication strategy is SAML or both, and `samlLogin()` redirects the current browser window to the configured SAML endpoint.

The automation therefore:

1. Opens the normal EncorePay sign-in page.
2. Clicks `Sign in with SSO` when `client.N.sso=true`.
3. Follows the same browser-window redirect instead of waiting for a new window.
4. If the identity provider displays a credential form, completes common username/password/continue controls from `LoginPage`.
5. If the existing SSO session is already authenticated, skips the provider form and waits for EncorePay to return.
6. Verifies that the authenticated EncorePay area is reached before continuing.

For `client.N.sso=false`, `LoginPage.login()` always uses the normal username/password form.

