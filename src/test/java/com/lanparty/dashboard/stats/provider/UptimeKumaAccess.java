package com.lanparty.dashboard.stats.provider;

import tools.jackson.databind.JsonNode;

/** Test access to package-private mapping helpers. */
public final class UptimeKumaAccess {

    private UptimeKumaAccess() {
    }

    public static JsonNode map(JsonNode page, JsonNode heartbeat) {
        return UptimeKumaProvider.map(page, heartbeat);
    }
}
