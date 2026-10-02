package com.lanparty.dashboard.server;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class GameServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String name;
    private String shortCode;
    private String host;
    private Integer port;
    private Integer queryPort;

    @Enumerated(EnumType.STRING)
    private QueryType queryType = QueryType.NONE;

    private String connectUrl;
    private boolean visible = true;
    private Instant availableFrom;
    private int sort;

    public String address() {
        return port == null ? host : host + ":" + port;
    }

    /** Explicit connect URL, or a steam:// link for Source servers. */
    public String effectiveConnectUrl() {
        if (connectUrl != null && !connectUrl.isBlank()) {
            return connectUrl;
        }
        return queryType == QueryType.SOURCE ? "steam://connect/" + address() : null;
    }

    public int effectiveQueryPort() {
        if (queryPort != null) {
            return queryPort;
        }
        if (port != null) {
            return port;
        }
        return switch (queryType) {
            case MINECRAFT -> 25565;
            case QUAKE3 -> 28960;
            default -> 27015;
        };
    }

    public GameServer copyTo(Long targetEventId) {
        GameServer copy = new GameServer();
        copy.eventId = targetEventId;
        copy.name = name;
        copy.shortCode = shortCode;
        copy.host = host;
        copy.port = port;
        copy.queryPort = queryPort;
        copy.queryType = queryType;
        copy.connectUrl = connectUrl;
        copy.visible = visible;
        copy.sort = sort;
        return copy;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public Integer getQueryPort() { return queryPort; }
    public void setQueryPort(Integer queryPort) { this.queryPort = queryPort; }
    public QueryType getQueryType() { return queryType; }
    public void setQueryType(QueryType queryType) { this.queryType = queryType; }
    public String getConnectUrl() { return connectUrl; }
    public void setConnectUrl(String connectUrl) { this.connectUrl = connectUrl; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public Instant getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(Instant availableFrom) { this.availableFrom = availableFrom; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
}
