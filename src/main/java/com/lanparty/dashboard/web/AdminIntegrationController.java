package com.lanparty.dashboard.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.stats.IntegrationService;
import com.lanparty.dashboard.stats.IntegrationService.IntegrationRequest;
import com.lanparty.dashboard.stats.IntegrationService.IntegrationView;
import com.lanparty.dashboard.stats.IntegrationService.ProviderInfo;
import com.lanparty.dashboard.stats.IntegrationService.TestResult;
import com.lanparty.dashboard.stats.StatsService;

@RestController
@RequestMapping("/api/admin")
public class AdminIntegrationController {

    private final AdminEvents adminEvents;
    private final IntegrationService integrations;
    private final StatsService stats;

    public AdminIntegrationController(AdminEvents adminEvents, IntegrationService integrations, StatsService stats) {
        this.adminEvents = adminEvents;
        this.integrations = integrations;
        this.stats = stats;
    }

    @GetMapping("/integration-types")
    public List<ProviderInfo> types() {
        return integrations.providers();
    }

    @GetMapping("/events/{eventId}/integrations")
    public List<IntegrationView> list(@PathVariable Long eventId) {
        return integrations.list(adminEvents.get(eventId));
    }

    @PostMapping("/events/{eventId}/integrations")
    public IntegrationView create(@PathVariable Long eventId, @RequestBody IntegrationRequest request) {
        return integrations.create(adminEvents.get(eventId), request);
    }

    @PutMapping("/events/{eventId}/integrations/{id}")
    public IntegrationView update(@PathVariable Long eventId, @PathVariable Long id, @RequestBody IntegrationRequest request) {
        return integrations.update(adminEvents.get(eventId), id, request);
    }

    @DeleteMapping("/events/{eventId}/integrations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long eventId, @PathVariable Long id) {
        integrations.delete(adminEvents.get(eventId), id);
        return ResponseEntity.noContent().build();
    }

    /** Tests a (possibly unsaved) configuration. Pass {@code id} to reuse stored secrets. */
    @PostMapping("/events/{eventId}/integrations/test")
    public TestResult test(@PathVariable Long eventId, @RequestParam(required = false) Long id, @RequestBody IntegrationRequest request) {
        return integrations.test(adminEvents.get(eventId), id, request);
    }

    @GetMapping("/events/{eventId}/metrics")
    public List<StatsService.Kpi> metrics(@PathVariable Long eventId) {
        return stats.view(adminEvents.get(eventId)).kpis();
    }

    @DeleteMapping("/events/{eventId}/metrics/{key}")
    public ResponseEntity<Map<String, String>> deleteMetric(@PathVariable Long eventId, @PathVariable String key) {
        stats.deleteMetric(adminEvents.get(eventId), key);
        return ResponseEntity.noContent().build();
    }
}
