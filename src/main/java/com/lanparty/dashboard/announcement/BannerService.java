package com.lanparty.dashboard.announcement;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.tournament.Tournament;
import com.lanparty.dashboard.tournament.TournamentRepository;

/** Combines manual announcements with automatic "registration closes soon" notices. */
@Service
public class BannerService {

    /** How long before a tournament's registration deadline the automatic banner appears. */
    public static final Duration REGISTRATION_WARNING = Duration.ofMinutes(30);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AnnouncementRepository announcements;
    private final TournamentRepository tournaments;
    private final Clock clock;

    public BannerService(AnnouncementRepository announcements, TournamentRepository tournaments, Clock clock) {
        this.announcements = announcements;
        this.tournaments = tournaments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Banner> current(Event event) {
        return build(event.zone(),
                announcements.findByEventIdOrderBySort(event.getId()),
                tournaments.findByEventIdOrderBySort(event.getId()),
                clock.instant());
    }

    static List<Banner> build(ZoneId zone, List<Announcement> manual, List<Tournament> tournamentList, Instant now) {
        List<Banner> banners = new ArrayList<>();
        for (Tournament t : tournamentList) {
            Instant closesAt = t.getRegistrationClosesAt();
            if (t.isRegistrationOpen() && closesAt != null
                    && !now.isBefore(closesAt.minus(REGISTRATION_WARNING)) && now.isBefore(closesAt)) {
                banners.add(new Banner("reg-" + t.getId(), Banner.Kind.REGISTRATION_CLOSING,
                        "Anmeldung " + t.getName() + " schliesst um " + TIME.format(closesAt.atZone(zone)),
                        closesAt));
            }
        }
        for (Announcement a : manual) {
            if (a.isVisibleAt(now)) {
                banners.add(new Banner("a-" + a.getId(), Banner.Kind.MANUAL, a.getText(), null));
            }
        }
        return banners;
    }
}
