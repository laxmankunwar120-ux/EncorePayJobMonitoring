package com.encorepay.notifiers;

import com.encorepay.utilities.ConfigReader;

public final class GoogleChatNotifier {

    private GoogleChatNotifier() {
    }

    public static void notify(String htmlReportPath) {
        String webhook = new ConfigReader().getGoogleChatWebhookUrl();

        if (webhook == null || webhook.isBlank()) {
            System.err.println("[ERROR] Google Chat notification skipped: GOOGLE_CHAT_WEBHOOK_URL is not"
                + " configured. Set it under Settings -> Secrets and variables -> Actions.");
            if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
                System.out.println("::error::GOOGLE_CHAT_WEBHOOK_URL is not set - no Google Chat notification"
                    + " was sent for this run.");
            }
            return;
        }

        try {
            GoogleChatApiNotifier.send(webhook, htmlReportPath);
        } catch (Exception reportFailure) {
            System.err.println("[ERROR] Google Chat notification failed: " + reportFailure.getMessage());
        }
    }
}
