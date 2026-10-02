package com.lanparty.dashboard.challonge;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.event.EventRepository;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.tournament.Registration;
import com.lanparty.dashboard.tournament.RegistrationRepository;
import com.lanparty.dashboard.tournament.Tournament;
import com.lanparty.dashboard.tournament.TournamentRepository;

/**
 * Keeps local tournaments and Challonge in sync: pushes new registrations as participants and
 * caches the bracket (snapshot) so the dashboard keeps working when the internet is flaky.
 */
@Service
public class ChallongeSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChallongeSyncService.class);

    private final ChallongeClient client;
    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final ChangeNotifier notifier;
    private final Clock clock;
    /** Serialises syncs so a registration is never pushed twice. */
    private final ReentrantLock lock = new ReentrantLock();

    public ChallongeSyncService(ChallongeClient client, TournamentRepository tournaments, RegistrationRepository registrations,
                                EventRepository events, ChangeNotifier notifier, Clock clock) {
        this.client = client;
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.events = events;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${lan.challonge-poll-ms:30000}")
    public void syncActiveEvent() {
        if (!client.configured()) {
            return;
        }
        events.findFirstByActiveTrue().ifPresent(event -> {
            for (Tournament t : tournaments.findByEventIdOrderBySort(event.getId())) {
                if (t.getChallongeSlug() == null) {
                    continue;
                }
                try {
                    sync(t.getId());
                } catch (RuntimeException e) {
                    log.warn("Challonge sync for '{}' failed: {}", t.getName(), e.getMessage());
                }
            }
        });
    }

    /** Called after a registration so the participant shows up on Challonge right away. */
    @Async
    public void syncSoon(Long tournamentId) {
        if (!client.configured()) {
            return;
        }
        try {
            sync(tournamentId);
        } catch (RuntimeException e) {
            log.warn("Challonge push failed, will retry on next poll: {}", e.getMessage());
        }
    }

    /** Pushes pending registrations and refreshes the snapshot. Throws on Challonge errors. */
    public void sync(Long tournamentId) {
        lock.lock();
        try {
            Tournament t = tournaments.findById(tournamentId).orElse(null);
            if (t == null || t.getChallongeSlug() == null) {
                return;
            }
            for (Registration r : registrations.findByTournamentIdOrderByCreatedAt(t.getId())) {
                if (r.getChallongeParticipantId() == null) {
                    long id = client.addParticipant(t.getChallongeSlug(), r.displayName(), misc(r));
                    r.setChallongeParticipantId(id);
                    registrations.save(r);
                }
            }
            String snapshot = client.fetchTournament(t.getChallongeSlug());
            if (!Objects.equals(snapshot, t.getChallongeSnapshot())) {
                t.setChallongeSnapshot(snapshot);
                t.setSnapshotAt(clock.instant());
                tournaments.save(t);
                notifier.publish(Topic.TOURNAMENTS);
            }
        } finally {
            lock.unlock();
        }
    }

    public void start(Long tournamentId) {
        Tournament t = tournaments.findById(tournamentId).orElseThrow();
        if (t.getChallongeSlug() == null) {
            throw new BadRequestException("Kein Challonge-Turnier verknüpft.");
        }
        sync(tournamentId);
        client.start(t.getChallongeSlug());
        sync(tournamentId);
    }

    private static String misc(Registration r) {
        StringBuilder misc = new StringBuilder(r.getGamertag());
        if (r.getTeammates() != null) {
            misc.append(" + ").append(r.getTeammates());
        }
        if (r.getSeatLabel() != null) {
            misc.append(" @ ").append(r.getSeatLabel());
        }
        return misc.toString();
    }
}
