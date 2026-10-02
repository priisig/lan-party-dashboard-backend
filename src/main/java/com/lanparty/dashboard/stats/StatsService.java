package com.lanparty.dashboard.stats;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.server.GameServer;
import com.lanparty.dashboard.server.GameServerQueryService;
import com.lanparty.dashboard.server.GameServerRepository;
import com.lanparty.dashboard.server.query.ServerStatus;

import tools.jackson.databind.JsonNode;

@Service
public class StatsService {

    private final MetricRepository metrics;
    private final IntegrationRepository integrations;
    private final GameServerRepository servers;
    private final GameServerQueryService queries;
    private final ChangeNotifier notifier;
    private final Clock clock;

    public StatsService(MetricRepository metrics, IntegrationRepository integrations, GameServerRepository servers,
                        GameServerQueryService queries, ChangeNotifier notifier, Clock clock) {
        this.metrics = metrics;
        this.integrations = integrations;
        this.servers = servers;
        this.queries = queries;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** @param tone optional colour hint for the frontend ("good", "bad") */
    public record Kpi(String key, String label, String value, String unit, String tone, Instant updatedAt) {
    }

    public record Widget(Long id, String type, String name, boolean ok, String error, Instant lastOkAt, JsonNode data) {
    }

    public record StatsView(List<Kpi> kpis, List<Widget> widgets, Instant serverTime) {
    }

    public record MetricPush(String key, String label, String value, String unit, Integer sort) {
    }

    @Transactional(readOnly = true)
    public StatsView view(Event event) {
        Instant now = clock.instant();
        List<Kpi> kpis = new ArrayList<>();
        for (Metric m : metrics.findByEventIdOrderBySortAscKeyAsc(event.getId())) {
            kpis.add(new Kpi(m.getKey(), m.getLabel(), m.getValue(), m.getUnit(), null, m.getUpdatedAt()));
        }

        List<GameServer> serverList = servers.findByEventIdOrderBySort(event.getId());
        int players = 0;
        boolean anyQueried = false;
        for (GameServer s : serverList) {
            ServerStatus status = queries.status(s.getId());
            if (status != null && status.online() && status.playersOnline() != null) {
                players += status.playersOnline();
                anyQueried = true;
            }
        }
        if (anyQueried) {
            kpis.add(new Kpi("players", "Spieler auf Servern", String.valueOf(players), null, "good", now));
        }
        if (!now.isBefore(event.getStartsAt())) {
            Instant until = now.isAfter(event.getEndsAt()) ? event.getEndsAt() : now;
            Duration running = Duration.between(event.getStartsAt(), until);
            kpis.add(new Kpi("uptime", "LAN läuft seit", "%d:%02d".formatted(running.toHours(), running.toMinutesPart()), "h", null, now));
        }

        List<Widget> widgets = new ArrayList<>();
        for (Integration i : integrations.findByEventIdOrderBySort(event.getId())) {
            if (!i.isEnabled()) {
                continue;
            }
            JsonNode data = i.getLastResult() == null ? null : Json.parse(i.getLastResult());
            widgets.add(new Widget(i.getId(), i.getType(), i.getName(), i.getLastError() == null && data != null,
                    i.getLastError(), i.getLastOkAt(), data));
        }
        return new StatsView(kpis, widgets, now);
    }

    @Transactional
    public int push(Event event, List<MetricPush> pushes) {
        Instant now = clock.instant();
        for (MetricPush p : pushes) {
            if (p.key() == null || !p.key().matches("[a-zA-Z0-9_.-]{1,60}") || p.value() == null) {
                throw new BadRequestException("Ungültige Metrik: key [a-zA-Z0-9_.-]{1,60} und value sind nötig.");
            }
            Metric metric = metrics.findByEventIdAndKey(event.getId(), p.key())
                    .orElseGet(() -> new Metric(event.getId(), p.key()));
            metric.update(p.label(), truncate(p.value(), 60), truncate(p.unit(), 30), p.sort(), now);
            metrics.save(metric);
        }
        notifier.publish(Topic.STATS);
        return pushes.size();
    }

    @Transactional
    public void deleteMetric(Event event, String key) {
        metrics.findByEventIdAndKey(event.getId(), key).ifPresent(metrics::delete);
        notifier.publish(Topic.STATS);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : Objects.toString(s);
    }
}
