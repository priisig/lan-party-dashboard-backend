package com.lanparty.dashboard.web;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.admin.Settings;
import com.lanparty.dashboard.event.ActiveEventService;
import com.lanparty.dashboard.stats.StatsService;
import com.lanparty.dashboard.stats.StatsService.MetricPush;

/**
 * Lets scripts push numbers into the Nerd Stats tiles, e.g.
 * {@code curl -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"
 * -d '[{"key":"clients","label":"LAN-Clients online","value":"87"}]' http://dashboard/api/push/metrics}
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final Settings settings;
    private final ActiveEventService activeEvent;
    private final StatsService stats;

    public PushController(Settings settings, ActiveEventService activeEvent, StatsService stats) {
        this.settings = settings;
        this.activeEvent = activeEvent;
        this.stats = stats;
    }

    @PutMapping("/metrics")
    public ResponseEntity<Map<String, Object>> push(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @RequestBody List<MetricPush> metrics) {
        String expected = settings.get(Settings.PUSH_TOKEN).orElse(null);
        String given = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        if (expected == null || given == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Ungültiger Push-Token."));
        }
        int count = stats.push(activeEvent.get(), metrics);
        return ResponseEntity.ok(Map.of("updated", count));
    }
}
