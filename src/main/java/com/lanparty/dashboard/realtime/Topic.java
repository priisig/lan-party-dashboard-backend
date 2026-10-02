package com.lanparty.dashboard.realtime;

/** Data areas the frontend caches separately; mirrors the query keys in the React app. */
public enum Topic {
    EVENT("event"),
    BANNERS("banners"),
    SCHEDULE("schedule"),
    SERVERS("servers"),
    TOURNAMENTS("tournaments"),
    SEATS("seats"),
    STATS("stats");

    private final String key;

    Topic(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
