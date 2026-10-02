package com.lanparty.dashboard.web;

import org.springframework.stereotype.Service;

import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.schedule.ScheduleService;
import com.lanparty.dashboard.schedule.ScheduleView;
import com.lanparty.dashboard.tournament.TournamentService;

/** Decides what the "LIVE · …" pill in the header shows. */
@Service
public class LiveService {

    private final TournamentService tournaments;
    private final ScheduleService schedule;

    public LiveService(TournamentService tournaments, ScheduleService schedule) {
        this.tournaments = tournaments;
        this.schedule = schedule;
    }

    /** @param text e.g. "CS2 5v5 – Viertelfinal"; null when nothing runs */
    public record LiveTag(String text, Long tournamentId) {
    }

    public LiveTag current(Event event) {
        var running = tournaments.underway(event);
        if (running.isPresent()) {
            var t = running.get().getKey();
            String round = running.get().getValue().currentRound();
            return new LiveTag(t.getName() + (round != null ? " – " + round : ""), t.getId());
        }
        ScheduleView.Entry live = schedule.view(event).live();
        if (live != null) {
            return new LiveTag(live.title(), live.tournamentId());
        }
        return new LiveTag(null, null);
    }
}
