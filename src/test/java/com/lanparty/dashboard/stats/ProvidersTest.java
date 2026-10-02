package com.lanparty.dashboard.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.stats.provider.ConfigField;
import com.lanparty.dashboard.stats.provider.IntegrationProvider;
import com.lanparty.dashboard.stats.provider.MinecraftProviderAccess;
import com.lanparty.dashboard.stats.provider.UptimeKumaAccess;

import tools.jackson.databind.JsonNode;

class ProvidersTest {

    @Test
    void mapsUptimeKumaStatusPage() {
        JsonNode page = Json.parse("""
                {"config":{"title":"LAN"},"publicGroupList":[{"name":"Server","monitorList":[{"id":1,"name":"CS2-1"},{"id":2,"name":"GMOD-1"}]}]}
                """);
        JsonNode beats = Json.parse("""
                {"heartbeatList":{"1":[{"status":1,"ping":2},{"status":1,"ping":3}],"2":[{"status":1,"ping":5},{"status":0,"ping":null}]},
                 "uptimeList":{"1_24":1,"2_24":0.912}}
                """);
        JsonNode result = UptimeKumaAccess.map(page, beats);
        assertThat(result.path("up").asInt()).isEqualTo(1);
        assertThat(result.path("total").asInt()).isEqualTo(2);
        JsonNode gmod = result.path("monitors").get(1);
        assertThat(gmod.path("status").asInt()).isZero();
        assertThat(gmod.path("ping").isNull()).isTrue();
        assertThat(gmod.path("uptime24").asDouble()).isEqualTo(0.912);
        assertThat(result.path("monitors").get(0).path("beats").size()).isEqualTo(2);
    }

    @Test
    void readsTpsAndDayFromRconOutput() {
        assertThat(MinecraftProviderAccess.firstNumber("§6TPS from last 1m, 5m, 15m: §a19.9, §a20.0, §a20.0")).isEqualTo(19.9);
        assertThat(MinecraftProviderAccess.firstNumber("The time is 37")).isEqualTo(37.0);
        assertThat(MinecraftProviderAccess.firstNumber("Unknown command")).isNull();
    }

    @Test
    void maskedSecretsKeepStoredValueAndUnknownKeysAreDropped() {
        IntegrationProvider provider = new IntegrationProvider() {
            public String type() { return "x"; }
            public String label() { return "X"; }
            public List<ConfigField> configFields() {
                return List.of(ConfigField.text("host", "Host", true, ""), ConfigField.password("secret", "Secret"));
            }
            public JsonNode fetch(JsonNode config) { return config; }
        };
        JsonNode stored = Json.parse("{\"host\":\"a\",\"secret\":\"s3cret\"}");
        JsonNode merged = IntegrationService.mergeSecrets(provider, stored,
                Json.parse("{\"host\":\"b\",\"secret\":\"" + IntegrationService.MASK + "\",\"junk\":1}"));
        assertThat(merged.path("host").asString()).isEqualTo("b");
        assertThat(merged.path("secret").asString()).isEqualTo("s3cret");
        assertThat(merged.has("junk")).isFalse();
    }
}
