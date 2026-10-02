package com.lanparty.dashboard.stats.provider;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * A data source for the Nerd Stats view. To add a new one, implement this interface as a Spring bean
 * and register a widget for the same {@link #type()} in the frontend (src/widgets/registry.tsx).
 */
public interface IntegrationProvider {

    /** Stable identifier stored in the database, e.g. "uptime-kuma". */
    String type();

    /** Name shown in the admin UI. */
    String label();

    List<ConfigField> configFields();

    /** Fetches fresh data. The returned JSON is cached and handed to the frontend widget as-is. */
    JsonNode fetch(JsonNode config) throws Exception;
}
