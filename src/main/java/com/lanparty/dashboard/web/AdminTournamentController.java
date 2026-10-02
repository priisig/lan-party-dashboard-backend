package com.lanparty.dashboard.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.challonge.ChallongeSyncService;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.tournament.TournamentDtos.AdminView;
import com.lanparty.dashboard.tournament.TournamentDtos.TournamentRequest;
import com.lanparty.dashboard.tournament.TournamentService;

@RestController
@RequestMapping("/api/admin/events/{eventId}/tournaments")
public class AdminTournamentController {

    private final AdminEvents adminEvents;
    private final TournamentService tournaments;
    private final ChallongeSyncService challonge;

    public AdminTournamentController(AdminEvents adminEvents, TournamentService tournaments, ChallongeSyncService challonge) {
        this.adminEvents = adminEvents;
        this.tournaments = tournaments;
        this.challonge = challonge;
    }

    @GetMapping
    public List<AdminView> list(@PathVariable Long eventId) {
        return tournaments.adminList(adminEvents.get(eventId));
    }

    @PostMapping
    public AdminView create(@PathVariable Long eventId, @Valid @RequestBody TournamentRequest request) {
        return tournaments.create(adminEvents.get(eventId), request);
    }

    @PutMapping("/{id}")
    public AdminView update(@PathVariable Long eventId, @PathVariable Long id, @Valid @RequestBody TournamentRequest request) {
        return tournaments.update(adminEvents.get(eventId), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long eventId, @PathVariable Long id) {
        tournaments.delete(adminEvents.get(eventId), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/registrations/{registrationId}")
    public List<AdminView> deleteRegistration(@PathVariable Long eventId, @PathVariable Long id, @PathVariable Long registrationId) {
        Event event = adminEvents.get(eventId);
        tournaments.deleteRegistration(event, id, registrationId);
        return tournaments.adminList(event);
    }

    /** Pushes pending sign-ups to Challonge and reloads the bracket now instead of waiting for the poll. */
    @PostMapping("/{id}/sync")
    public List<AdminView> sync(@PathVariable Long eventId, @PathVariable Long id) {
        Event event = adminEvents.get(eventId);
        tournaments.find(event, id);
        challonge.sync(id);
        return tournaments.adminList(event);
    }

    @PostMapping("/{id}/start")
    public List<AdminView> start(@PathVariable Long eventId, @PathVariable Long id) {
        Event event = adminEvents.get(eventId);
        tournaments.find(event, id);
        challonge.start(id);
        return tournaments.adminList(event);
    }
}
