package com.lanparty.dashboard.stats;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.common.NotFoundException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.event.EventRepository;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.stats.provider.ConfigField;
import com.lanparty.dashboard.stats.provider.IntegrationProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Admin management of integrations plus the background poller that refreshes their data. */
@Service
public class IntegrationService {

    /** Placeholder returned instead of stored passwords; sending it back keeps the stored value. */
    public static final String MASK = "********";
    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final IntegrationRepository integrations;
    private final EventRepository events;
    private final Map<String, IntegrationProvider> providers;
    private final ChangeNotifier notifier;
    private final Clock clock;

    public IntegrationService(IntegrationRepository integrations, EventRepository events, List<IntegrationProvider> providers,
                              ChangeNotifier notifier, Clock clock) {
        this.integrations = integrations;
        this.events = events;
        this.providers = providers.stream().collect(Collectors.toMap(IntegrationProvider::type, Function.identity()));
        this.notifier = notifier;
        this.clock = clock;
    }

    public record ProviderInfo(String type, String label, List<ConfigField> fields) {
    }

    public record IntegrationView(Long id, String type, String name, boolean enabled, int sort, JsonNode config,
                                  String lastError, java.time.Instant lastOkAt) {
    }

    public record IntegrationRequest(String type, String name, boolean enabled, int sort, JsonNode config) {
    }

    public record TestResult(boolean ok, String message, JsonNode data) {
    }

    public List<ProviderInfo> providers() {
        return providers.values().stream()
                .map(p -> new ProviderInfo(p.type(), p.label(), p.configFields()))
                .sorted((a, b) -> a.label().compareToIgnoreCase(b.label()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IntegrationView> list(Event event) {
        return integrations.findByEventIdOrderBySort(event.getId()).stream().map(this::view).toList();
    }

    @Transactional
    public IntegrationView create(Event event, IntegrationRequest request) {
        IntegrationProvider provider = provider(request.type());
        Integration integration = new Integration(event.getId(), provider.type(), name(request, provider),
                Json.write(request.config() == null ? Json.object() : request.config()), request.sort());
        integration.setEnabled(request.enabled());
        integrations.save(integration);
        return view(integration);
    }

    @Transactional
    public IntegrationView update(Event event, Long id, IntegrationRequest request) {
        Integration integration = find(event, id);
        IntegrationProvider provider = provider(integration.getType());
        integration.setName(name(request, provider));
        integration.setEnabled(request.enabled());
        integration.setSort(request.sort());
        integration.setConfig(Json.write(mergeSecrets(provider, Json.parse(integration.getConfig()), request.config())));
        integration.setLastError(null);
        notifier.publish(Topic.STATS);
        return view(integration);
    }

    @Transactional
    public void delete(Event event, Long id) {
        integrations.delete(find(event, id));
        notifier.publish(Topic.STATS);
    }

    /** Tries a config without saving it (secrets masked in the request are taken from the stored integration). */
    @Transactional(readOnly = true)
    public TestResult test(Event event, Long id, IntegrationRequest request) {
        IntegrationProvider provider = provider(request.type());
        JsonNode config = request.config() == null ? Json.object() : request.config();
        if (id != null) {
            config = mergeSecrets(provider, Json.parse(find(event, id).getConfig()), config);
        }
        try {
            JsonNode data = provider.fetch(config);
            return new TestResult(true, "Verbunden", data);
        } catch (Exception e) {
            return new TestResult(false, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    @Scheduled(initialDelay = 4_000, fixedDelayString = "${lan.integration-poll-ms:20000}")
    public void poll() {
        events.findFirstByActiveTrue().ifPresent(event -> {
            boolean changed = false;
            for (Integration integration : integrations.findByEventIdOrderBySort(event.getId())) {
                if (integration.isEnabled()) {
                    changed |= refresh(integration);
                }
            }
            if (changed) {
                notifier.publish(Topic.STATS);
            }
        });
    }

    private boolean refresh(Integration integration) {
        IntegrationProvider provider = providers.get(integration.getType());
        if (provider == null) {
            return false;
        }
        String previousResult = integration.getLastResult();
        String previousError = integration.getLastError();
        try {
            integration.setLastResult(Json.write(provider.fetch(Json.parse(integration.getConfig()))));
            integration.setLastError(null);
            integration.setLastOkAt(clock.instant());
        } catch (Exception e) {
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            integration.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
            log.debug("Integration '{}' failed: {}", integration.getName(), message);
        }
        integrations.save(integration);
        return !Objects.equals(previousResult, integration.getLastResult()) || !Objects.equals(previousError, integration.getLastError());
    }

    private IntegrationView view(Integration i) {
        IntegrationProvider provider = providers.get(i.getType());
        JsonNode config = Json.parse(i.getConfig());
        if (provider != null && config instanceof ObjectNode object) {
            for (ConfigField field : provider.configFields()) {
                if (field.kind().equals("password") && !Json.text(object, field.name(), "").isBlank()) {
                    object.put(field.name(), MASK);
                }
            }
        }
        return new IntegrationView(i.getId(), i.getType(), i.getName(), i.isEnabled(), i.getSort(), config, i.getLastError(), i.getLastOkAt());
    }

    static JsonNode mergeSecrets(IntegrationProvider provider, JsonNode stored, JsonNode incoming) {
        ObjectNode result = incoming instanceof ObjectNode o ? o.deepCopy() : Json.object();
        for (ConfigField field : provider.configFields()) {
            if (field.kind().equals("password") && MASK.equals(Json.text(result, field.name(), ""))) {
                JsonNode old = stored.get(field.name());
                if (old == null) {
                    result.remove(field.name());
                } else {
                    result.set(field.name(), old);
                }
            }
        }
        // Drop unknown keys so typos in old configs don't linger forever.
        List<String> unknown = new java.util.ArrayList<>();
        result.propertyNames().forEach(key -> {
            if (provider.configFields().stream().noneMatch(f -> f.name().equals(key))) {
                unknown.add(key);
            }
        });
        unknown.forEach(result::remove);
        return result;
    }

    private IntegrationProvider provider(String type) {
        IntegrationProvider provider = type == null ? null : providers.get(type);
        if (provider == null) {
            throw new BadRequestException("Unbekannter Integrationstyp: " + type);
        }
        return provider;
    }

    private Integration find(Event event, Long id) {
        return integrations.findById(id)
                .filter(i -> i.getEventId().equals(event.getId()))
                .orElseThrow(() -> new NotFoundException("Integration nicht gefunden."));
    }

    private static String name(IntegrationRequest request, IntegrationProvider provider) {
        return request.name() == null || request.name().isBlank() ? provider.label() : request.name().trim();
    }
}
