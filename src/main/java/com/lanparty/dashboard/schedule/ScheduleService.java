package com.lanparty.dashboard.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.event.Event;

@Service
public class ScheduleService {

    /** Items before 06:00 still belong to the previous LAN day (the 01:00 "Free Play" is Saturday night). */
    static final LocalTime DAY_CUTOFF = LocalTime.of(6, 0);
    /** Assumed length of the last item when it has no explicit end. */
    static final Duration DEFAULT_LAST_DURATION = Duration.ofHours(1);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    static final Locale DE = Locale.forLanguageTag("de-CH");

    private final ScheduleItemRepository items;
    private final Clock clock;

    public ScheduleService(ScheduleItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScheduleView view(Event event) {
        return build(event, items.findByEventIdOrderByStartsAt(event.getId()), clock.instant());
    }

    static ScheduleView build(Event event, List<ScheduleItem> sorted, Instant now) {
        ZoneId zone = event.zone();
        List<ScheduleView.Entry> entries = new ArrayList<>();
        boolean nextAssigned = false;
        for (int i = 0; i < sorted.size(); i++) {
            ScheduleItem item = sorted.get(i);
            Instant end = effectiveEnd(sorted, i);
            ScheduleStatus status;
            if (!now.isBefore(end)) {
                status = ScheduleStatus.DONE;
            } else if (!now.isBefore(item.getStartsAt())) {
                status = ScheduleStatus.LIVE;
            } else if (!nextAssigned) {
                status = ScheduleStatus.NEXT;
                nextAssigned = true;
            } else {
                status = ScheduleStatus.PLANNED;
            }
            entries.add(new ScheduleView.Entry(item.getId(), item.getStartsAt(), end,
                    TIME.format(item.getStartsAt().atZone(zone)), item.getTitle(), item.getLocation(),
                    item.getColor(), item.getTournamentId(), status));
        }

        Map<LocalDate, List<ScheduleView.Entry>> byDay = new LinkedHashMap<>();
        for (LocalDate d = lanDay(event.getStartsAt(), zone); !d.isAfter(lanDay(event.getEndsAt(), zone)); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }
        for (ScheduleView.Entry e : entries) {
            byDay.computeIfAbsent(lanDay(e.startsAt(), zone), d -> new ArrayList<>()).add(e);
        }
        List<ScheduleView.Day> days = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> new ScheduleView.Day(en.getKey().toString(), dayLabel(en.getKey()), en.getValue()))
                .toList();

        LocalDate today = lanDay(now, zone);
        String currentDay = days.stream().map(ScheduleView.Day::key)
                .filter(k -> k.equals(today.toString())).findFirst()
                .orElse(days.isEmpty() ? null : (now.isBefore(event.getStartsAt()) ? days.getFirst().key() : days.getLast().key()));

        ScheduleView.Entry live = entries.stream().filter(e -> e.status() == ScheduleStatus.LIVE).findFirst().orElse(null);
        ScheduleView.Entry next = entries.stream().filter(e -> e.status() == ScheduleStatus.NEXT).findFirst().orElse(null);
        return new ScheduleView(days, currentDay, live, next);
    }

    static Instant effectiveEnd(List<ScheduleItem> sorted, int index) {
        ScheduleItem item = sorted.get(index);
        if (item.getEndsAt() != null) {
            return item.getEndsAt();
        }
        for (int j = index + 1; j < sorted.size(); j++) {
            if (sorted.get(j).getStartsAt().isAfter(item.getStartsAt())) {
                return sorted.get(j).getStartsAt();
            }
        }
        return item.getStartsAt().plus(DEFAULT_LAST_DURATION);
    }

    static LocalDate lanDay(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        return local.toLocalTime().isBefore(DAY_CUTOFF) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }

    static String dayLabel(LocalDate date) {
        String label = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, DE).replace(".", "");
        return label.length() > 2 ? label.substring(0, 2) : label;
    }
}
