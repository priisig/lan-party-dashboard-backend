package com.lanparty.dashboard.stats;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A value pushed by an external script (router, monitoring…) and shown as a tile on Nerd Stats. */
@Entity
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String key;
    private String label;
    private String value;
    private String unit;
    private int sort;
    private Instant updatedAt;

    protected Metric() {
    }

    public Metric(Long eventId, String key) {
        this.eventId = eventId;
        this.key = key;
    }

    public void update(String label, String value, String unit, Integer sort, Instant now) {
        this.label = label == null || label.isBlank() ? key : label;
        this.value = value;
        this.unit = unit;
        if (sort != null) {
            this.sort = sort;
        }
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public int getSort() { return sort; }
    public Instant getUpdatedAt() { return updatedAt; }
}
