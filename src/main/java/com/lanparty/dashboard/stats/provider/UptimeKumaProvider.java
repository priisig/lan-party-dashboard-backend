package com.lanparty.dashboard.stats.provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.lanparty.dashboard.common.Json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads a public Uptime Kuma status page:
 * {@code /api/status-page/{slug}} (monitor list) and {@code /api/status-page/heartbeat/{slug}} (beats + uptime).
 */
@Component
public class UptimeKumaProvider implements IntegrationProvider {

    static final int BEATS = 48;

    private final RestClient http;

    public UptimeKumaProvider(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(factory).build();
    }

    @Override
    public String type() {
        return "uptime-kuma";
    }

    @Override
    public String label() {
        return "Uptime Kuma · Status-Page";
    }

    @Override
    public List<ConfigField> configFields() {
        return List.of(
                ConfigField.url("baseUrl", "Uptime Kuma URL", "http://10.0.0.5:3001"),
                ConfigField.text("slug", "Status-Page Slug", true, "lan"));
    }

    @Override
    public JsonNode fetch(JsonNode config) {
        String base = Json.text(config, "baseUrl", "").replaceAll("/+$", "");
        String slug = Json.text(config, "slug", "");
        if (base.isBlank() || slug.isBlank()) {
            throw new IllegalArgumentException("URL und Slug sind nötig");
        }
        String page = http.get().uri(base + "/api/status-page/{slug}", slug).retrieve().body(String.class);
        String beats = http.get().uri(base + "/api/status-page/heartbeat/{slug}", slug).retrieve().body(String.class);
        return map(Json.parse(page), Json.parse(beats));
    }

    /** Status codes: 0 down, 1 up, 2 pending, 3 maintenance. */
    static JsonNode map(JsonNode page, JsonNode heartbeat) {
        ObjectNode result = Json.object();
        result.put("title", page.path("config").path("title").asString(""));
        ArrayNode monitors = result.putArray("monitors");
        int up = 0;
        int total = 0;
        for (JsonNode group : page.path("publicGroupList")) {
            for (JsonNode m : group.path("monitorList")) {
                String id = m.path("id").asString();
                JsonNode list = heartbeat.path("heartbeatList").path(id);
                List<JsonNode> all = new ArrayList<>();
                list.forEach(all::add);
                List<JsonNode> recent = all.subList(Math.max(0, all.size() - BEATS), all.size());

                ObjectNode monitor = monitors.addObject();
                monitor.put("id", m.path("id").asLong());
                monitor.put("name", m.path("name").asString());
                monitor.put("group", group.path("name").asString(""));
                ArrayNode beats = monitor.putArray("beats");
                recent.forEach(b -> beats.add(b.path("status").asInt()));
                JsonNode last = recent.isEmpty() ? null : recent.getLast();
                int status = last == null ? 2 : last.path("status").asInt();
                monitor.put("status", status);
                if (last != null && !last.path("ping").isNull()) {
                    monitor.put("ping", last.path("ping").asInt());
                } else {
                    monitor.putNull("ping");
                }
                JsonNode uptime = heartbeat.path("uptimeList").path(id + "_24");
                if (uptime.isNumber()) {
                    monitor.put("uptime24", uptime.asDouble());
                } else {
                    monitor.putNull("uptime24");
                }
                total++;
                if (status == 1 || status == 3) {
                    up++;
                }
            }
        }
        result.put("up", up);
        result.put("total", total);
        return result;
    }
}
