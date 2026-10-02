package com.lanparty.dashboard.event;

import java.time.Instant;
import java.time.ZoneId;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.lanparty.dashboard.seating.SeatOrientation;

/** One LAN edition, e.g. "VIVO LAN 2026". Exactly one event is active and shown on the dashboard. */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;
    private String title;
    private String subtitle;
    private String location;
    private String timezone = "Europe/Zurich";
    private Instant startsAt;
    private Instant endsAt;
    private boolean active;
    private String welcomeTitle;
    private String welcomeText;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    private byte[] logo;

    private String logoContentType;

    @Enumerated(EnumType.STRING)
    private SeatOrientation seatOrientation = SeatOrientation.ROWS;

    private boolean seatRowsReversed;
    private boolean seatNumbersReversed;
    private int kioskIntervalSec = 30;
    private String kioskViews = "overview,tournaments,seating,stats";
    private Instant createdAt = Instant.now();

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getWelcomeTitle() { return welcomeTitle; }
    public void setWelcomeTitle(String welcomeTitle) { this.welcomeTitle = welcomeTitle; }
    public String getWelcomeText() { return welcomeText; }
    public void setWelcomeText(String welcomeText) { this.welcomeText = welcomeText; }
    public byte[] getLogo() { return logo; }
    public void setLogo(byte[] logo) { this.logo = logo; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }
    public SeatOrientation getSeatOrientation() { return seatOrientation; }
    public void setSeatOrientation(SeatOrientation seatOrientation) { this.seatOrientation = seatOrientation; }
    public boolean isSeatRowsReversed() { return seatRowsReversed; }
    public void setSeatRowsReversed(boolean seatRowsReversed) { this.seatRowsReversed = seatRowsReversed; }
    public boolean isSeatNumbersReversed() { return seatNumbersReversed; }
    public void setSeatNumbersReversed(boolean seatNumbersReversed) { this.seatNumbersReversed = seatNumbersReversed; }
    public int getKioskIntervalSec() { return kioskIntervalSec; }
    public void setKioskIntervalSec(int kioskIntervalSec) { this.kioskIntervalSec = kioskIntervalSec; }
    public String getKioskViews() { return kioskViews; }
    public void setKioskViews(String kioskViews) { this.kioskViews = kioskViews; }
    public Instant getCreatedAt() { return createdAt; }
}
