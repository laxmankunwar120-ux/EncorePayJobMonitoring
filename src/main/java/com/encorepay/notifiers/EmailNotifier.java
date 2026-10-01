package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
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
    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";
    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private EmailNotifier() {}

    public static void notify(List<JobStatus> statuses, String htmlReportPath) {
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

            int failed = (int) statuses.stream().filter(EmailNotifier::isFailed).count();
            String subjectPrefix = failed > 0 ? "⚠️" : "✅";
           message.setSubject(subjectPrefix + " EncorePay Job Monitoring Report");

            String plainText = buildPlainText(statuses);
            String html = buildHtml(statuses);

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
            // The report never reached the recipients, so this run did not do its job. It is
            // raised for the run to record rather than logged and forgotten.
            System.out.println("[FAIL] Email notification failed (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            throw e instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException("Email notification failed: " + e.getMessage(), e);
        }
    }

    private static String buildPlainText(List<JobStatus> statuses) {
        int clients = (int) statuses.stream().map(s -> safe(s.getClientName())).distinct().count();
        long successful = statuses.stream().filter(EmailNotifier::isSuccessful).count();
        long failed = statuses.stream().filter(EmailNotifier::isFailed).count();
        long attention = statuses.stream().filter(s -> !isSuccessful(s) && !isFailed(s)).count();

        StringBuilder body = new StringBuilder();
        body.append("ENCOREPAY JOB MONITORING REPORT\n")
                .append("Generated: ").append(LocalDateTime.now().format(REPORT_TIME)).append("\n")
                .append("Clients: ").append(clients)
                .append(" | Successful: ").append(successful)
                .append(" | Failed: ").append(failed)
                .append(" | Attention: ").append(attention)
                .append("\n\n");

        appendPlainSection(body, "POST RECEIPTS JOB", statuses, POST_RECEIPTS, true);
        appendPlainSection(body, "DOWNLOAD COLLECTION ITEMS JOB", statuses, COLLECTIONS, false);
        appendPlainSection(body, "UPCOMING DEMAND JOB", statuses, UPCOMING, false);

        body.append(failed == 0 && attention == 0
                ? "Overall Status: HEALTHY\n"
                : "Action Required: Please investigate failed/attention entries.\n")
                .append("\nEncorePay Job Monitor");
        return body.toString();
    }

    private static void appendPlainSection(StringBuilder body, String heading, List<JobStatus> statuses, String jobName, boolean counts) {
        List<JobStatus> rows = statuses.stream()
                .filter(s -> jobName.equalsIgnoreCase(s.getJobName()))
                .toList();
        if (rows.isEmpty()) return;

        body.append(heading).append("\n");
        for (JobStatus s : rows) {
            body.append(statusIcon(s)).append(" ")
                    .append(safe(s.getClientName()))
                    .append(" - ").append(displayStatus(s.getStatus()));
            if (counts) {
                body.append(" | Failed: ").append(s.getFailedCount())
                        .append(" | Pending: ").append(s.getPendingCount());
            }
            body.append(" | Last Run: ").append(safe(s.getDateTime())).append("\n");
            String reason = conciseReason(s);
            if (!reason.isBlank()) body.append("   Error: ").append(reason).append("\n");
        }
        body.append("\n");
    }

    private static String buildHtml(List<JobStatus> statuses) {
        long successful = statuses.stream().filter(EmailNotifier::isSuccessful).count();
        long failed = statuses.stream().filter(EmailNotifier::isFailed).count();
        long attention = statuses.stream().filter(s -> !isSuccessful(s) && !isFailed(s)).count();
        String overallClass = failed == 0 && attention == 0 ? "ok" : "alert";
        String overallText = failed == 0 && attention == 0 ? "HEALTHY" : "ACTION REQUIRED";

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
                .append("<div class='header'><h1>📊 EncorePay Job Monitoring Report</h1><div class='meta'>Generated: ")
                .append(escape(LocalDateTime.now().format(REPORT_TIME))).append("</div></div>")
                .append("<div class='summary'>")
                .append(metric("Successful", successful))
                .append(metric("Failed", failed))
                .append(metric("Attention", attention))
                .append("</div>");

        appendHtmlSection(html, "🧾 Post Receipts Job", statuses, POST_RECEIPTS, true);
        appendHtmlSection(html, "📥 Download Collection Items Job", statuses, COLLECTIONS, false);
        appendHtmlSection(html, "📈 Upcoming Demand Job", statuses, UPCOMING, false);

        html.append("<div class='").append(overallClass).append("'>")
                .append(failed == 0 && attention == 0 ? "🟢 Overall Status: " : "🚨 ")
                .append(overallText)
                .append("</div><div class='footer'>EncorePay Job Monitor</div></div></body></html>");
        return html.toString();
    }

    private static void appendHtmlSection(StringBuilder html, String title, List<JobStatus> statuses, String jobName, boolean counts) {
        List<JobStatus> rows = statuses.stream().filter(s -> jobName.equalsIgnoreCase(s.getJobName())).toList();
        if (rows.isEmpty()) return;

        html.append("<div class='section'><h2>").append(escape(title)).append("</h2><table><thead><tr>")
                .append("<th>Client</th><th>Status</th>");
        if (counts) html.append("<th>Failed</th><th>Pending</th>");
        html.append("<th>Last Run</th><th>Details</th></tr></thead><tbody>");

        for (JobStatus s : rows) {
            String status = displayStatus(s.getStatus());
            String css = isFailed(s) ? "failed" : isSuccessful(s) ? "success" : "attention";
            html.append("<tr><td><b class='client'>").append(escape(safe(s.getClientName()))).append("</b></td>")
                    .append("<td class='").append(css).append("'>").append(escape(status)).append("</td>");
            if (counts) {
                html.append("<td>").append(s.getFailedCount()).append("</td><td>").append(s.getPendingCount()).append("</td>");
            }
            html.append("<td>").append(escape(safe(s.getDateTime()))).append("</td>")
                    .append("<td class='reason'>").append(escape(conciseReason(s))).append("</td></tr>");
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
        if (normalized.matches("(?s).*\\b\\d{3}\\s+[A-Za-z][A-Za-z ]{2,40}.*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)(\\d{3})\\s+([A-Za-z][A-Za-z ]{2,40})")
                    .matcher(normalized);
            if (m.find()) return (m.group(1) + " " + m.group(2)).trim();
        }
        if (normalized.length() > 220) normalized = normalized.substring(0, 217).trim() + "...";
        return normalized;
    }

    private static boolean isSuccessful(JobStatus s) {
        String v = safe(s == null ? null : s.getStatus()).toUpperCase(Locale.ROOT);
        return v.contains("SUCCESS") || v.contains("COMPLETED") || v.equals("SUCCEEDED");
    }

    private static boolean isFailed(JobStatus s) {
        return safe(s == null ? null : s.getStatus()).toUpperCase(Locale.ROOT).contains("FAIL");
    }

    private static String statusIcon(JobStatus s) {
        return isSuccessful(s) ? "[OK]" : isFailed(s) ? "[FAILED]" : "[ATTENTION]";
    }

    private static String displayStatus(String status) {
        String v = safe(status).toUpperCase(Locale.ROOT);
        if (v.contains("SUCCESS") || v.contains("COMPLETED") || v.equals("SUCCEEDED")) return "SUCCESSFUL";
        if (v.contains("FAIL")) return "FAILED";
        return v.isBlank() ? "NOT CAPTURED" : v.toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

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
