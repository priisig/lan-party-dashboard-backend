package com.lanparty.dashboard.info;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A label/value pair in the "Wichtige Infos" card (WLAN, Voice, Hausregeln…). */
@Entity
public class InfoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String label;
    private String value;
    private int sort;

    protected InfoItem() {
    }

    public InfoItem(Long eventId, String label, String value, int sort) {
        this.eventId = eventId;
        this.label = label;
        this.value = value;
        this.sort = sort;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public int getSort() { return sort; }
}
