package com.lanparty.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapAdminName  name of the admin created on first start when no admin exists
 * @param bootstrapAdminCode  code of that admin; nothing is created when empty
 * @param seedDemo            creates a demo event mirroring the design prototype when the database has no events
 * @param challongeBaseUrl    Challonge API v1 base URL
 */
@ConfigurationProperties(prefix = "lan")
public record LanProperties(
        String bootstrapAdminName,
        String bootstrapAdminCode,
        boolean seedDemo,
        String challongeBaseUrl) {

    public LanProperties {
        if (bootstrapAdminName == null || bootstrapAdminName.isBlank()) {
            bootstrapAdminName = "Admin";
        }
        if (challongeBaseUrl == null || challongeBaseUrl.isBlank()) {
            challongeBaseUrl = "https://api.challonge.com/v1";
        }
    }
}
