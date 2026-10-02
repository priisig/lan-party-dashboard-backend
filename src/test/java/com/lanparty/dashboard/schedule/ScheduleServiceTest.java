package com.lanparty.dashboard.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lanparty.dashboard.event.Event;

class ScheduleServiceTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private static Instant local(String dateTime) {
        return java.time.LocalDateTime.parse(dateTime).atZone(ZURICH).toInstant();
    }

    private static Event event() {
        Event e = new Event();
        e.setTimezone(ZURICH.getId());
        e.setStartsAt(local("2026-10-16T18:00"));
        e.setEndsAt(local("2026-10-18T14:00"));
        return e;
    }

    private static ScheduleItem item(String start, String title) {
        return new ScheduleItem(1L, local(start), null, title, null, "#fff", null);
    }

    @Test
    void computesDoneLiveNextAndPlanned() {
        var items = List.of(
                item("2026-10-17T14:00", "Gruppenphase"),
                item("2026-10-17T16:30", "KO-Runde"),
                item("2026-10-17T19:00", "Abendessen"),
                item("2026-10-17T20:00", "CoD4"));
        ScheduleView view = ScheduleService.build(event(), items, local("2026-10-17T17:42"));
        var sat = view.days().stream().filter(d -> d.key().equals("2026-10-17")).findFirst().orElseThrow();
        assertThat(sat.items()).extracting(ScheduleView.Entry::status)
                .containsExactly(ScheduleStatus.DONE, ScheduleStatus.LIVE, ScheduleStatus.NEXT, ScheduleStatus.PLANNED);
        assertThat(view.live().title()).isEqualTo("KO-Runde");
        assertThat(view.live().endsAt()).isEqualTo(local("2026-10-17T19:00"));
        assertThat(view.next().title()).isEqualTo("Abendessen");
        assertThat(view.currentDay()).isEqualTo("2026-10-17");
    }

    @Test
    void lastItemWithoutEndLastsOneHour() {
        var items = List.of(item("2026-10-17T22:00", "Prop Hunt"));
        assertThat(ScheduleService.build(event(), items, local("2026-10-17T22:59")).live()).isNotNull();
        assertThat(ScheduleService.build(event(), items, local("2026-10-17T23:00")).live()).isNull();
    }

    @Test
    void nightItemsBelongToThePreviousLanDay() {
        assertThat(ScheduleService.lanDay(local("2026-10-18T01:00"), ZURICH)).isEqualTo(LocalDate.parse("2026-10-17"));
        assertThat(ScheduleService.lanDay(local("2026-10-18T06:00"), ZURICH)).isEqualTo(LocalDate.parse("2026-10-18"));

        var view = ScheduleService.build(event(), List.of(item("2026-10-18T01:00", "Free Play")), local("2026-10-17T12:00"));
        assertThat(view.days()).extracting(ScheduleView.Day::label).containsExactly("Fr", "Sa", "So");
        assertThat(view.days().get(1).items()).extracting(ScheduleView.Entry::title).containsExactly("Free Play");
    }
}
