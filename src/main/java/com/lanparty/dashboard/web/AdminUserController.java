package com.lanparty.dashboard.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.auth.UserPrincipal;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.seating.Seat;
import com.lanparty.dashboard.seating.SeatRepository;
import com.lanparty.dashboard.seating.SeatStatus;
import com.lanparty.dashboard.seating.SeatingService;
import com.lanparty.dashboard.server.GameServerService;
import com.lanparty.dashboard.tournament.TournamentDtos.StatusKind;
import com.lanparty.dashboard.tournament.TournamentService;
import com.lanparty.dashboard.user.AppUser;
import com.lanparty.dashboard.user.Participant;
import com.lanparty.dashboard.user.ParticipantRepository;
import com.lanparty.dashboard.user.UserDtos.AdminUserView;
import com.lanparty.dashboard.user.UserDtos.EnabledUpdate;
import com.lanparty.dashboard.user.UserDtos.ParticipantUpdate;
import com.lanparty.dashboard.user.UserDtos.PasswordReset;
import com.lanparty.dashboard.user.UserDtos.RoleUpdate;
import com.lanparty.dashboard.user.UserService;

/** Accounts, roles and per-event participant state (payment, check-in), plus the KPI row of the admin panel. */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final UserService users;
    private final ParticipantRepository participants;
    private final SeatRepository seats;
    private final SeatingService seating;
    private final TournamentService tournaments;
    private final GameServerService servers;
    private final AdminEvents adminEvents;

    public AdminUserController(UserService users, ParticipantRepository participants, SeatRepository seats,
                               SeatingService seating, TournamentService tournaments, GameServerService servers,
                               AdminEvents adminEvents) {
        this.users = users;
        this.participants = participants;
        this.seats = seats;
        this.seating = seating;
        this.tournaments = tournaments;
        this.servers = servers;
        this.adminEvents = adminEvents;
    }

    /** All accounts; {@code participant} marks those taking part in this event. */
    @GetMapping("/events/{eventId}/participants")
    @Transactional(readOnly = true)
    public List<AdminUserView> participants(@PathVariable Long eventId) {
        Event event = adminEvents.get(eventId);
        Map<Long, Participant> byUser = participants.findByEventId(event.getId()).stream()
                .collect(Collectors.toMap(Participant::getUserId, p -> p));
        Map<Long, String> seatByUser = new HashMap<>();
        for (Seat s : seats.findByEventId(event.getId())) {
            if (s.getUserId() != null) {
                seatByUser.put(s.getUserId(), s.getLabel());
            }
        }
        return users.list().stream().map(u -> view(u, byUser.get(u.getId()), seatByUser.get(u.getId()))).toList();
    }

    @PutMapping("/events/{eventId}/participants/{userId}")
    @Transactional
    public AdminUserView updateParticipant(@PathVariable Long eventId, @PathVariable Long userId,
                                           @Valid @RequestBody ParticipantUpdate request) {
        Event event = adminEvents.get(eventId);
        AppUser user = users.get(userId);
        Participant p = users.participant(event, userId);
        p.setPaid(request.paid());
        p.setCheckedIn(request.checkedIn());
        String seat = seats.findFirstByEventIdAndUserId(event.getId(), userId).map(Seat::getLabel).orElse(null);
        return view(user, p, seat);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Void> role(@PathVariable Long id, @Valid @RequestBody RoleUpdate request,
                                     @AuthenticationPrincipal UserPrincipal me) {
        users.setRole(id, request.role(), me.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<Void> enabled(@PathVariable Long id, @RequestBody EnabledUpdate request,
                                        @AuthenticationPrincipal UserPrincipal me) {
        users.setEnabled(id, request.enabled(), me.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/password-reset")
    public PasswordReset resetPassword(@PathVariable Long id) {
        return new PasswordReset(users.resetPassword(id));
    }

    public record Overview(int participants, int checkedIn, int paymentOpen, int seatsTaken, int seatsCapacity,
                           int pendingRequests, int liveTournaments, int serversOnline, int serversTotal) {
    }

    @GetMapping("/events/{eventId}/overview")
    @Transactional(readOnly = true)
    public Overview overview(@PathVariable Long eventId) {
        Event event = adminEvents.get(eventId);
        List<Participant> list = participants.findByEventId(event.getId());
        List<Seat> seatList = seats.findByEventId(event.getId());
        var serverList = servers.publicList(event);
        return new Overview(
                list.size(),
                (int) list.stream().filter(Participant::isCheckedIn).count(),
                (int) list.stream().filter(p -> !p.isPaid()).count(),
                (int) seatList.stream().filter(s -> s.getStatus() == SeatStatus.TAKEN).count(),
                (int) seatList.stream().filter(s -> s.getStatus() != SeatStatus.BLOCKED).count(),
                seating.pending(event).size(),
                (int) tournaments.summaries(event).stream().filter(t -> t.statusKind() == StatusKind.LIVE).count(),
                (int) serverList.stream().filter(GameServerService.PublicServer::online).count(),
                serverList.size());
    }

    private static AdminUserView view(AppUser u, Participant p, String seat) {
        return new AdminUserView(u.getId(), u.getNickname(), u.getEmail(), u.getRole(), u.isEnabled(), u.getFirstName(),
                u.getLastName(), u.getCreatedAt(), p != null, seat, p != null && p.isPaid(), p != null && p.isCheckedIn());
    }
}
