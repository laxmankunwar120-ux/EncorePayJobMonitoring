package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
import com.encorepay.models.ReportData;
import com.encorepay.utilities.ConfigReader;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class EmailNotifier {
    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private EmailNotifier() {}

    public static void notify(List<JobStatus> statuses, String htmlReportPath) {
        notify(statuses, List.of(), List.of(), htmlReportPath);
    }

    public static void notify(
            List<JobStatus> statuses,
            List<String> clientFailures,
            List<String> configuredClients,
            String htmlReportPath) {
        ConfigReader config = new ConfigReader();
        if (!config.isEmailNotificationEnabled()) {
            System.out.println("[WARN] Email notification is disabled. Set reportEmailEnabled=true or REPORT_EMAIL_ENABLED=true.");
            return;
        }

        String to = config.getEmailTo();
        String username = config.getSmtpUsername();
        String password = config.getSmtpPassword();
        String host = config.getSmtpHost();
        List<String> missingSettings = new ArrayList<>();
        if (to.isBlank()) missingSettings.add("REPORT_EMAIL_TO/reportEmailTo");
        if (username.isBlank()) missingSettings.add("SMTP_USERNAME/smtpUsername");
        if (password.isBlank()) missingSettings.add("SMTP_PASSWORD/smtpPassword");
        if (host.isBlank()) missingSettings.add("SMTP_HOST/smtpHost");

        if (!missingSettings.isEmpty()) {
            System.out.println("[WARN] Email notification skipped. Missing required setting(s): " + String.join(", ", missingSettings) + ".");
            return;
        }
        if (statuses == null || statuses.isEmpty()) {
            System.out.println("[WARN] Email notification skipped because no job monitoring data was provided.");
            return;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "30000");
            props.put("mail.smtp.writetimeout", "30000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if (!config.getEmailCc().isBlank()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(config.getEmailCc()));
            }

            ReportData data = ReportData.from(statuses, clientFailures, configuredClients);
            boolean hasFailures = data.getFailedJobs() > 0 || data.getAttentionJobs() > 0;
            message.setSubject(hasFailures ? "Action Required - EncorePay Job Monitoring Report"
                    : "EncorePay Job Monitoring Report");

            String plainText = buildPlainText(data);
            String html = buildHtml(data);

            MimeBodyPart plainPart = new MimeBodyPart();
            plainPart.setText(plainText, StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html; charset=UTF-8");

            MimeMultipart alternatives = new MimeMultipart("alternative");
            alternatives.addBodyPart(plainPart);
            alternatives.addBodyPart(htmlPart);

            MimeBodyPart contentPart = new MimeBodyPart();
            contentPart.setContent(alternatives);

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(contentPart);

            boolean htmlAttached = attach(multipart, htmlReportPath);
            if (!htmlAttached) {
                throw new IllegalStateException("No report attachment was available.");
            }

            message.setContent(multipart);
            Transport.send(message);
            System.out.println("[INFO] Email notification sent to " + to + ".");
        } catch (Exception e) {

            System.out.println("[FAIL] Email notification failed (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            throw e instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException("Email notification failed: " + e.getMessage(), e);
        }
    }

    private static String buildPlainText(ReportData data) {
        LocalDateTime now = LocalDateTime.now(new ConfigReader().getBusinessZone());
        StringBuilder body = new StringBuilder();
        body.append("ENCOREPAY JOB MONITORING REPORT\n")
                .append("Generated: ").append(now.format(REPORT_TIME)).append("\n")
                .append("Clients: ").append(data.getTotalClients())
                .append(" | Monitored: ").append(data.getMonitoredClients())
                .append(" | Successful: ").append(data.getSuccessfulJobs())
                .append(" | Failed: ").append(data.getFailedJobs())
                .append(" | Attention: ").append(data.getAttentionJobs())
                .append(" | Failed Receipts: ").append(Math.max(0, data.getTotalFailedReceipts()))
                .append(" | Pending Receipts: ").append(Math.max(0, data.getTotalPendingReceipts()))
                .append("\n\n");

        appendPlainSection(body, "POST RECEIPTS JOB", data.getPostReceipts(), true);
        appendPlainSection(body, "DOWNLOAD COLLECTION ITEMS JOB", data.getCollectionJobs(), false);
        appendPlainSection(body, "UPCOMING DEMAND JOB", data.getUpcomingJobs(), false);

        appendClientFailures(body, data);

        body.append(data.getFailedJobs() == 0 && data.getAttentionJobs() == 0
                ? "Overall Status: HEALTHY\n"
                : "Action Required: Please investigate failed/attention entries.\n")
                .append("\nEncorePay Job Monitor");
        return body.toString();
    }

    private static void appendPlainSection(StringBuilder body, String heading, List<JobStatus> statuses, boolean counts) {
        if (statuses.isEmpty()) return;

        body.append(heading).append("\n");
        for (JobStatus s : statuses) {
            body.append(statusLabel(s)).append(" ")
                    .append(safe(s.getClientName()))
                    .append(" - ").append(s.getDisplayStatus());
            if (counts) {
                body.append(" | Failed: ").append(displayCount(s.getFailedCount()))
                        .append(" | Pending: ").append(displayCount(s.getPendingCount()));
            }
            body.append(" | Last Run: ").append(safe(s.getDateTime())).append("\n");
            String reason = conciseReason(s);
            if (!reason.isBlank()) body.append("   Error: ").append(reason).append("\n");
        }
        body.append("\n");
    }

    private static void appendClientFailures(StringBuilder body, ReportData data) {
        List<ReportData.ClientException> exceptions = data.getClientExceptions();
        if (exceptions.isEmpty()) return;

        body.append("CLIENT EXCEPTIONS\n");
        for (ReportData.ClientException ex : exceptions) {
            if (ex.state == ReportData.ClientState.NOT_RUN) continue;
            body.append(ex.client).append(" - ").append(ex.state).append("\n");
            for (String detail : ex.details) {
                body.append("   ").append(detail).append("\n");
            }
        }
        body.append("\n");
    }

    private static String buildHtml(ReportData data) {
        LocalDateTime now = LocalDateTime.now(new ConfigReader().getBusinessZone());
        String timestamp = now.format(REPORT_TIME);
        boolean hasFailures = data.getFailedJobs() > 0 || data.getAttentionJobs() > 0;

        StringBuilder html = new StringBuilder(12000);
        html.append("<!doctype html><html><head><meta charset='UTF-8'>")
                .append("<style>")
                .append("body{font-family:Segoe UI,Arial,sans-serif;background:#f4f6f8;margin:0;padding:24px;color:#17202a}")
                .append(".card{max-width:900px;margin:auto;background:#fff;border:1px solid #dfe5ea;border-radius:12px;overflow:hidden}")
                .append(".header{padding:24px;background:#f8fafc;border-bottom:1px solid #e5e7eb}")
                .append("h1{font-size:22px;margin:0 0 6px}.meta{color:#64748b;font-size:13px}")
                .append(".summary{display:flex;gap:12px;padding:18px 24px;flex-wrap:wrap}")
                .append(".metric{border:1px solid #e2e8f0;border-radius:10px;padding:12px 16px;min-width:120px}")
                .append(".metric b{display:block;font-size:20px}.metric span{font-size:12px;color:#64748b}")
                .append(".section{padding:0 24px 18px}.section h2{font-size:15px;margin:14px 0 8px;padding:10px 12px;background:#eef4f8;border-left:4px solid #2f5d7c}")
                .append("table{width:100%;border-collapse:collapse;font-size:13px}th,td{border:1px solid #e2e8f0;padding:9px;text-align:left;vertical-align:top}th{background:#f8fafc;font-size:12px}")
                .append(".success{font-weight:700}.failed{font-weight:700}.attention{font-weight:700}.reason{font-size:12px;color:#dc2626;font-weight:600}")
                .append(".client{font-weight:700;color:#b45309}")
                .append(".ok,.alert{margin:4px 24px 24px;padding:14px;border-radius:9px;font-weight:700}.ok{background:#ecfdf5;color:#166534}.alert{background:#fff7ed;color:#9a3412}")
                .append(".footer{padding:18px 24px;color:#64748b;font-size:12px;border-top:1px solid #e5e7eb}")
                .append("</style></head><body><div class='card'>")
                .append("<div class='header'><h1>EncorePay Job Monitoring Report</h1><div class='meta'>Generated: ")
                .append(escape(timestamp)).append("</div></div>")
                .append("<div class='summary'>")
                .append(metric("Successful", data.getSuccessfulJobs()))
                .append(metric("Failed", data.getFailedJobs()))
                .append(metric("Attention", data.getAttentionJobs()))
                .append("</div>");

        appendHtmlSection(html, "Post Receipts Job", data.getPostReceipts(), true);
        appendHtmlSection(html, "Download Collection Items Job", data.getCollectionJobs(), false);
        appendHtmlSection(html, "Upcoming Demand Job", data.getUpcomingJobs(), false);

        appendHtmlClientFailures(html, data);

        html.append("<div class='").append(hasFailures ? "alert" : "ok").append("'>")
                .append(hasFailures ? "Action Required: " : "Overall Status: ")
                .append(hasFailures ? "Please investigate failed/attention entries." : "HEALTHY")
                .append("</div><div class='footer'>EncorePay Job Monitor</div></div></body></html>");
        return html.toString();
    }

    private static void appendHtmlSection(StringBuilder html, String title, List<JobStatus> statuses, boolean counts) {
        if (statuses.isEmpty()) return;

        html.append("<div class='section'><h2>").append(escape(title)).append("</h2><table><thead><tr>")
                .append("<th>Client</th><th>Status</th>");
        if (counts) html.append("<th>Failed</th><th>Pending</th>");
        html.append("<th>Last Run</th><th>Details</th></tr></thead><tbody>");

        for (JobStatus s : statuses) {
            String status = s.getDisplayStatus();
            String css = s.isFailed() ? "failed" : s.isSuccessful() ? "success" : "attention";
            html.append("<tr><td><b class='client'>").append(escape(safe(s.getClientName()))).append("</b></td>")
                    .append("<td class='").append(css).append("'>").append(escape(status)).append("</td>");
            if (counts) {
                html.append("<td>").append(displayCount(s.getFailedCount())).append("</td><td>").append(displayCount(s.getPendingCount())).append("</td>");
            }
            html.append("<td>").append(escape(safe(s.getDateTime()))).append("</td>")
                    .append("<td class='reason'>").append(escape(conciseReason(s))).append("</td></tr>");
        }
        html.append("</tbody></table></div>");
    }

    private static void appendHtmlClientFailures(StringBuilder html, ReportData data) {
        List<ReportData.ClientException> exceptions = data.getClientExceptions();
        if (exceptions.isEmpty()) return;

        html.append("<div class='section'><h2>Client Exceptions</h2><table><thead><tr>")
                .append("<th>Client</th><th>Status</th><th>Details</th></tr></thead><tbody>");
        for (ReportData.ClientException ex : exceptions) {
            if (ex.state == ReportData.ClientState.NOT_RUN) continue;
            html.append("<tr><td><b class='client'>").append(escape(ex.client)).append("</b></td>")
                    .append("<td>").append(escape(ex.state.toString())).append("</td>")
                    .append("<td class='reason'>").append(escape(String.join("; ", ex.details))).append("</td></tr>");
        }
        html.append("</tbody></table></div>");
    }

    private static String metric(String label, long value) {
        return "<div class='metric'><b>" + value + "</b><span>" + label + "</span></div>";
    }

    private static String conciseReason(JobStatus status) {
        if (status == null) return "";
        String reason = safe(status.getJobFailureReason());
        if (reason.isBlank() && status.getFailureReasons() != null && !status.getFailureReasons().isEmpty()) {
            reason = String.join("; ", status.getFailureReasons());
        }
        if (reason.isBlank()) return "";

        String normalized = reason.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 220) normalized = normalized.substring(0, 217).trim() + "...";
        return normalized;
    }

    private static String statusLabel(JobStatus s) {
        if (s == null) return "[ATTENTION]";
        if (s.isFailed()) return "[FAILED]";
        if (s.isSuccessful()) return "[OK]";
        return "[ATTENTION]";
    }

    private static String displayCount(int count) {
        return count < 0 ? "N/A" : String.valueOf(count);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static boolean attach(MimeMultipart multipart, String path) throws MessagingException {
        if (path == null || path.isBlank()) {
            System.out.println("[WARN] Report attachment skipped because its path is empty.");
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("[WARN] Report attachment skipped because the file does not exist: " + path);
            return false;
        }
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new FileDataSource(file)));
        part.setFileName(file.getName());
        multipart.addBodyPart(part);
        return true;
    }
}