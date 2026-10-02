package com.lanparty.dashboard.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lanparty.dashboard.tournament.Tournament;

class BannerServiceTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final Instant CLOSES = Instant.parse("2026-10-17T17:30:00Z"); // 19:30 local

    private static Tournament tournament(boolean open, Instant closesAt) {
        Tournament t = new Tournament();
        t.setName("CoD4 2v2");
        t.setRegistrationOpen(open);
        t.setRegistrationClosesAt(closesAt);
        return t;
    }

    @Test
    void autoBannerAppearsExactlyThirtyMinutesBeforeClosing() {
        var t = List.of(tournament(true, CLOSES));
        assertThat(BannerService.build(ZURICH, List.of(), t, CLOSES.minusSeconds(30 * 60 + 1))).isEmpty();
        assertThat(BannerService.build(ZURICH, List.of(), t, CLOSES.minusSeconds(30 * 60)))
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.kind()).isEqualTo(Banner.Kind.REGISTRATION_CLOSING);
                    assertThat(b.text()).isEqualTo("Anmeldung CoD4 2v2 schliesst um 19:30");
                    assertThat(b.countdownTo()).isEqualTo(CLOSES);
                });
        assertThat(BannerService.build(ZURICH, List.of(), t, CLOSES.minusSeconds(1))).hasSize(1);
        assertThat(BannerService.build(ZURICH, List.of(), t, CLOSES)).isEmpty();
    }

    @Test
    void noAutoBannerWhenRegistrationIsClosedOrHasNoDeadline() {
        Instant now = CLOSES.minusSeconds(600);
        assertThat(BannerService.build(ZURICH, List.of(), List.of(tournament(false, CLOSES)), now)).isEmpty();
        assertThat(BannerService.build(ZURICH, List.of(), List.of(tournament(true, null)), now)).isEmpty();
    }

    @Test
    void manualAnnouncementsRespectTheirWindowAndComeAfterAutoBanners() {
        Instant now = CLOSES.minusSeconds(600);
        var manual = List.of(
                new Announcement(1L, "Pizza bis 18:30", true, null, null, 0),
                new Announcement(1L, "Disabled", false, null, null, 1),
                new Announcement(1L, "Later", true, now.plusSeconds(60), null, 2),
                new Announcement(1L, "Expired", true, null, now, 3));
        var banners = BannerService.build(ZURICH, manual, List.of(tournament(true, CLOSES)), now);
        assertThat(banners).extracting(Banner::text).containsExactly("Anmeldung CoD4 2v2 schliesst um 19:30", "Pizza bis 18:30");
    }
}
