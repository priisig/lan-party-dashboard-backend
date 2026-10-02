package com.lanparty.dashboard.web;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.auth.UserPrincipal;
import com.lanparty.dashboard.challonge.ChallongeSyncService;
import com.lanparty.dashboard.event.ActiveEventService;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.seating.SeatingDtos.ReservationRequest;
import com.lanparty.dashboard.seating.SeatingService;
import com.lanparty.dashboard.tournament.TournamentDtos.Detail;
import com.lanparty.dashboard.tournament.TournamentDtos.RegistrationRequest;
import com.lanparty.dashboard.tournament.TournamentService;
import com.lanparty.dashboard.user.AppUser;
import com.lanparty.dashboard.user.Participant;
import com.lanparty.dashboard.user.ParticipantRepository;
import com.lanparty.dashboard.user.UserDtos.Me;
import com.lanparty.dashboard.user.UserDtos.MyEvent;
import com.lanparty.dashboard.user.UserDtos.PasswordChange;
import com.lanparty.dashboard.user.UserDtos.Profile;
import com.lanparty.dashboard.user.UserService;

/** Everything a logged-in participant does for themselves: profile, seat, tournament sign-ups. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService users;
    private final ParticipantRepository participants;
    private final ActiveEventService activeEvent;
    private final SeatingService seating;
    private final TournamentService tournaments;
    private final ChallongeSyncService challonge;

    public MeController(UserService users, ParticipantRepository participants, ActiveEventService activeEvent,
                        SeatingService seating, TournamentService tournaments, ChallongeSyncService challonge) {
        this.users = users;
        this.participants = participants;
        this.activeEvent = activeEvent;
        this.seating = seating;
        this.tournaments = tournaments;
        this.challonge = challonge;
    }

    @GetMapping("/profile")
    public Profile profile(@AuthenticationPrincipal UserPrincipal me) {
        return Profile.of(users.get(me.id()));
    }

    @PutMapping("/profile")
    public Me updateProfile(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody Profile request) {
        return Me.of(users.updateProfile(me.id(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody PasswordChange request) {
        users.changePassword(me.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** My seat, payment / check-in and tournaments in the active event. */
    @GetMapping("/event")
    public MyEvent event(@AuthenticationPrincipal UserPrincipal me) {
        Event event = activeEvent.get();
        var seat = seating.mySeat(event, me.id());
        var participant = participants.findByEventIdAndUserId(event.getId(), me.id());
        return new MyEvent(seat.seat(), seat.pending(),
                participant.map(Participant::isPaid).orElse(false),
                participant.map(Participant::isCheckedIn).orElse(false),
                tournaments.mine(event, me.id()),
                users.lanCount(me.id()));
    }

    @PostMapping("/seat/{label}")
    public MyEvent reserve(@AuthenticationPrincipal UserPrincipal me, @PathVariable String label,
                           @Valid @RequestBody(required = false) ReservationRequest request) {
        Event event = activeEvent.get();
        AppUser user = users.get(me.id());
        users.participant(event, user.getId());
        seating.reserve(event, user, label, request);
        return event(me);
    }

    @DeleteMapping("/seat")
    public MyEvent cancelSeat(@AuthenticationPrincipal UserPrincipal me) {
        seating.cancel(activeEvent.get(), me.id());
        return event(me);
    }

    @PostMapping("/tournaments/{id}")
    public Detail register(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                           @Valid @RequestBody RegistrationRequest request) {
        Event event = activeEvent.get();
        AppUser user = users.get(me.id());
        users.participant(event, user.getId());
        tournaments.register(event, id, user, seating.mySeat(event, user.getId()).seat(), request);
        challonge.syncSoon(id);
        return tournaments.detail(event, id);
    }

    @DeleteMapping("/tournaments/{id}")
    public Detail withdraw(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        Event event = activeEvent.get();
        tournaments.withdraw(event, id, me.id());
        return tournaments.detail(event, id);
    }
}
