package com.lanparty.dashboard.announcement;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Manual "Durchsage" shown in the banner under the header, optionally limited to a time window. */
@Entity
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String text;

    @Enumerated(EnumType.STRING)
    private Banner.Tone kind = Banner.Tone.INFO;

    private boolean enabled = true;
    private Instant startsAt;
    private Instant endsAt;
    private int sort;

    protected Announcement() {
    }

    public Announcement(Long eventId, String text, Banner.Tone kind, boolean enabled, Instant startsAt, Instant endsAt, int sort) {
        this.eventId = eventId;
        this.text = text;
        this.kind = kind == null ? Banner.Tone.INFO : kind;
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.sort = sort;
    }

    public boolean isVisibleAt(Instant now) {
        return enabled
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt));
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getText() { return text; }
    public Banner.Tone getKind() { return kind; }
    public boolean isEnabled() { return enabled; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getSort() { return sort; }
}
