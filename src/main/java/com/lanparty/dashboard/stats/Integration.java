package com.lanparty.dashboard.stats;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A configured data source for the Nerd Stats view (Uptime Kuma status page, Minecraft server, …).
 * {@code type} selects the {@link com.lanparty.dashboard.stats.provider.IntegrationProvider};
 * {@code config} is provider-specific JSON.
 */
@Entity
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String type;
    private String name;
    private boolean enabled = true;
    private int sort;
    private String config = "{}";
    private String lastResult;
    private String lastError;
    private Instant lastOkAt;

    protected Integration() {
    }

    public Integration(Long eventId, String type, String name, String config, int sort) {
        this.eventId = eventId;
        this.type = type;
        this.name = name;
        this.config = config;
        this.sort = sort;
    }

    public Integration copyTo(Long targetEventId) {
        return new Integration(targetEventId, type, name, config, sort);
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getLastOkAt() { return lastOkAt; }
    public void setLastOkAt(Instant lastOkAt) { this.lastOkAt = lastOkAt; }
}
