package com.lanparty.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapAdminNickname nickname of the organiser account created on start when no organiser exists
 * @param bootstrapAdminEmail    e-mail (login) of that account; nothing is created when empty
 * @param bootstrapAdminPassword password of that account (≥ 8 characters)
 * @param seedDemo               creates a demo event mirroring the design prototype when the database has no events
 * @param challongeBaseUrl       Challonge API v1 base URL
 */
@ConfigurationProperties(prefix = "lan")
public record LanProperties(
        String bootstrapAdminNickname,
        String bootstrapAdminEmail,
        String bootstrapAdminPassword,
        boolean seedDemo,
        String challongeBaseUrl) {

    public LanProperties {
        if (bootstrapAdminNickname == null || bootstrapAdminNickname.isBlank()) {
            bootstrapAdminNickname = "Orga";
        }
        if (challongeBaseUrl == null || challongeBaseUrl.isBlank()) {
            challongeBaseUrl = "https://api.challonge.com/v1";
        }
    }
}
