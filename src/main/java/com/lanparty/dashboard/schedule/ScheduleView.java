package com.lanparty.dashboard.schedule;

import java.time.Instant;
import java.util.List;

/** Schedule grouped into LAN days, with status computed against server time. */
public record ScheduleView(List<Day> days, String currentDay, Entry live, Entry next) {

    public record Day(String key, String label, List<Entry> items) {
    }

    public record Entry(Long id, Instant startsAt, Instant endsAt, String time, String title, String location,
                        String color, Long tournamentId, ScheduleStatus status) {
    }
}
