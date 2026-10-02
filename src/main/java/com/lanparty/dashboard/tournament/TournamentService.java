package com.lanparty.dashboard.tournament;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.challonge.Bracket;
import com.lanparty.dashboard.challonge.BracketMapper;
import com.lanparty.dashboard.challonge.ChallongeClient;
import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.NotFoundException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.server.GameServer;
import com.lanparty.dashboard.server.GameServerRepository;
import com.lanparty.dashboard.tournament.TournamentDtos.AdminRegistration;
import com.lanparty.dashboard.tournament.TournamentDtos.AdminView;
import com.lanparty.dashboard.tournament.TournamentDtos.Detail;
import com.lanparty.dashboard.tournament.TournamentDtos.RegistrationRequest;
import com.lanparty.dashboard.user.AppUser;
import com.lanparty.dashboard.user.UserDtos.MyTournament;
import com.lanparty.dashboard.tournament.TournamentDtos.StatusKind;
import com.lanparty.dashboard.tournament.TournamentDtos.Summary;
import com.lanparty.dashboard.tournament.TournamentDtos.TournamentRequest;

@Service
public class TournamentService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale DE = Locale.forLanguageTag("de-CH");

    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final GameServerRepository servers;
    private final ChangeNotifier notifier;
    private final Clock clock;
    /** Parsed brackets keyed by tournament id, invalidated when the snapshot timestamp changes. */
    private final Map<Long, CachedBracket> bracketCache = new ConcurrentHashMap<>();

    private record CachedBracket(Instant snapshotAt, Bracket bracket) {
    }

    public TournamentService(TournamentRepository tournaments, RegistrationRepository registrations,
                             GameServerRepository servers, ChangeNotifier notifier, Clock clock) {
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.servers = servers;
        this.notifier = notifier;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public List<Summary> summaries(Event event) {
        List<Tournament> list = tournaments.findByEventIdOrderBySort(event.getId());
        Map<Long, Long> counts = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : registrations.countGrouped(list.stream().map(Tournament::getId).toList())) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, String> serverNames = serverNames(event);
        return list.stream().map(t -> summary(event, t, bracket(t), counts.getOrDefault(t.getId(), 0L).intValue(), serverNames)).toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(Event event, Long id) {
        Tournament t = find(event, id);
        Bracket bracket = bracket(t);
        List<String> participants = participants(t, bracket);
        return new Detail(summary(event, t, bracket, participants.size(), serverNames(event)), bracket, participants, t.getSnapshotAt());
    }

    /** First tournament whose Challonge bracket is running, used for the LIVE tag in the header. */
    @Transactional(readOnly = true)
    public java.util.Optional<Map.Entry<Tournament, Bracket>> underway(Event event) {
        for (Tournament t : tournaments.findByEventIdOrderBySort(event.getId())) {
            Bracket b = bracket(t);
            if (b != null && b.underway()) {
                return java.util.Optional.of(Map.entry(t, b));
            }
        }
        return java.util.Optional.empty();
    }

    @Transactional
    public Registration register(Event event, Long tournamentId, AppUser user, String seatLabel, RegistrationRequest request) {
        Tournament t = find(event, tournamentId);
        if (registrations.findByTournamentIdAndUserId(t.getId(), user.getId()).isPresent()) {
            throw new BadRequestException("Du bist für " + t.getName() + " bereits angemeldet.");
        }
        Instant now = clock.instant();
        if (!t.acceptsRegistrations(now)) {
            throw new BadRequestException("Die Anmeldung für " + t.getName() + " ist geschlossen.");
        }
        if (!request.rulesAccepted()) {
            throw new BadRequestException("Bitte bestätige, dass du die Turnierregeln gelesen hast.");
        }
        String teamName = blankToNull(request.teamName());
        if (t.isTeamTournament() && teamName == null) {
            throw new BadRequestException("Für dieses Teamturnier braucht es einen Teamnamen.");
        }
        List<String> current = participants(t, bracket(t));
        if (current.size() >= t.getMaxParticipants()) {
            throw new BadRequestException(t.getName() + " ist bereits voll (" + t.getMaxParticipants() + ").");
        }
        String display = teamName != null ? teamName : user.getNickname();
        if (current.stream().anyMatch(p -> p.equalsIgnoreCase(display))) {
            throw new BadRequestException("«" + display + "» ist bereits angemeldet.");
        }
        try {
            Registration saved = registrations.saveAndFlush(new Registration(t.getId(), user.getNickname(), teamName,
                    blankToNull(request.teammates()), seatLabel, user.getId()));
            notifier.publish(Topic.TOURNAMENTS);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("«" + display + "» ist bereits angemeldet.");
        }
    }

    /** Withdraws the user's sign-up as long as it isn't in the Challonge bracket yet. */
    @Transactional
    public void withdraw(Event event, Long tournamentId, Long userId) {
        Tournament t = find(event, tournamentId);
        Registration r = registrations.findByTournamentIdAndUserId(t.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Du bist für " + t.getName() + " nicht angemeldet."));
        if (r.getChallongeParticipantId() != null || bracket(t) != null && bracket(t).underway()) {
            throw new BadRequestException("Du bist schon im Turnierbaum – bitte wende dich an die Orga.");
        }
        registrations.delete(r);
        notifier.publish(Topic.TOURNAMENTS);
    }

    @Transactional(readOnly = true)
    public List<MyTournament> mine(Event event, Long userId) {
        List<Tournament> list = tournaments.findByEventIdOrderBySort(event.getId());
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Registration> byTournament = new HashMap<>();
        registrations.findByUserIdAndTournamentIdIn(userId, list.stream().map(Tournament::getId).toList())
                .forEach(r -> byTournament.put(r.getTournamentId(), r));
        Map<Long, String> serverNames = new HashMap<>();
        List<MyTournament> out = new java.util.ArrayList<>();
        for (Tournament t : list) {
            Registration r = byTournament.get(t.getId());
            if (r != null) {
                Summary s = summary(event, t, bracket(t), 0, serverNames);
                out.add(new MyTournament(t.getId(), t.getName(), t.getColor(), r.getTeamName(), s.statusText()));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<AdminView> adminList(Event event) {
        return tournaments.findByEventIdOrderBySort(event.getId()).stream().map(this::adminView).toList();
    }

    @Transactional
    public AdminView create(Event event, TournamentRequest request) {
        Tournament t = new Tournament();
        t.setEventId(event.getId());
        apply(t, request);
        tournaments.save(t);
        notifier.publish(Topic.TOURNAMENTS);
        return adminView(t);
    }

    @Transactional
    public AdminView update(Event event, Long id, TournamentRequest request) {
        Tournament t = find(event, id);
        String oldSlug = t.getChallongeSlug();
        apply(t, request);
        if (!Objects.equals(oldSlug, t.getChallongeSlug())) {
            t.setChallongeSnapshot(null);
            t.setSnapshotAt(null);
            bracketCache.remove(t.getId());
        }
        notifier.publish(Topic.TOURNAMENTS);
        notifier.publish(Topic.BANNERS);
        return adminView(t);
    }

    @Transactional
    public void delete(Event event, Long id) {
        tournaments.delete(find(event, id));
        bracketCache.remove(id);
        notifier.publish(Topic.TOURNAMENTS);
    }

    @Transactional
    public void deleteRegistration(Event event, Long tournamentId, Long registrationId) {
        Tournament t = find(event, tournamentId);
        Registration r = registrations.findById(registrationId)
                .filter(reg -> reg.getTournamentId().equals(t.getId()))
                .orElseThrow(() -> new NotFoundException("Anmeldung nicht gefunden."));
        registrations.delete(r);
        notifier.publish(Topic.TOURNAMENTS);
    }

    // ---------------------------------------------------------------- helpers

    public Tournament find(Event event, Long id) {
        return tournaments.findById(id)
                .filter(t -> t.getEventId().equals(event.getId()))
                .orElseThrow(() -> new NotFoundException("Turnier nicht gefunden."));
    }

    Bracket bracket(Tournament t) {
        if (t.getChallongeSnapshot() == null) {
            return null;
        }
        CachedBracket cached = bracketCache.get(t.getId());
        if (cached != null && Objects.equals(cached.snapshotAt(), t.getSnapshotAt())) {
            return cached.bracket();
        }
        Bracket bracket = BracketMapper.map(t.getChallongeSnapshot());
        bracketCache.put(t.getId(), new CachedBracket(t.getSnapshotAt(), bracket));
        return bracket;
    }

    /** Local registrations first, then participants that were added directly on Challonge. */
    private List<String> participants(Tournament t, Bracket bracket) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        registrations.findByTournamentIdOrderByCreatedAt(t.getId()).forEach(r -> names.add(r.displayName()));
        if (bracket != null) {
            for (String p : bracket.participants()) {
                if (names.stream().noneMatch(n -> n.equalsIgnoreCase(p))) {
                    names.add(p);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private Summary summary(Event event, Tournament t, Bracket bracket, int registered, Map<Long, String> serverNames) {
        Instant now = clock.instant();
        ZoneId zone = event.zone();
        StatusKind kind;
        String text;
        if (bracket != null && bracket.underway()) {
            kind = StatusKind.LIVE;
            text = "● Läuft" + (bracket.currentRound() != null ? " · " + bracket.currentRound() : "");
        } else if (bracket != null && "complete".equals(bracket.state())) {
            kind = StatusKind.DONE;
            text = "Beendet";
        } else if (t.acceptsRegistrations(now)) {
            kind = StatusKind.OPEN;
            text = "Anmeldung offen" + (t.getStartsAt() != null ? " · Start " + when(t.getStartsAt(), now, zone, false) : "");
        } else if (t.getStartsAt() != null && t.getStartsAt().isAfter(now)) {
            kind = StatusKind.PLANNED;
            text = when(t.getStartsAt(), now, zone, true);
        } else {
            kind = StatusKind.CLOSED;
            text = "Anmeldung geschlossen";
        }
        int count = bracket == null ? registered : Math.max(registered, bracket.participants().size());
        return new Summary(t.getId(), t.getName(), t.getColor(), t.getFormatLabel(), kind, text, t.getMaxParticipants(),
                count, t.getTeamSize(), t.acceptsRegistrations(now), t.getRegistrationClosesAt(), t.getStartsAt(),
                t.getServerId() == null ? null : serverNames.get(t.getServerId()), t.challongeUrl(), t.getRulesUrl());
    }

    /** "20:00" today, otherwise "Sa 20:00" (or "Sonntag 11:00" in long form). */
    private static String when(Instant at, Instant now, ZoneId zone, boolean longDay) {
        var local = at.atZone(zone);
        String time = TIME.format(local);
        if (local.toLocalDate().equals(now.atZone(zone).toLocalDate())) {
            return time;
        }
        String day = local.getDayOfWeek().getDisplayName(longDay ? TextStyle.FULL : TextStyle.SHORT, DE).replace(".", "");
        return day + " " + time;
    }

    private Map<Long, String> serverNames(Event event) {
        Map<Long, String> names = new HashMap<>();
        for (GameServer s : servers.findByEventIdOrderBySort(event.getId())) {
            names.put(s.getId(), s.getShortCode() != null && !s.getShortCode().isBlank() ? s.getShortCode() : s.getName());
        }
        return names;
    }

    private AdminView adminView(Tournament t) {
        List<AdminRegistration> regs = registrations.findByTournamentIdOrderByCreatedAt(t.getId()).stream()
                .map(r -> new AdminRegistration(r.getId(), r.getGamertag(), r.getTeamName(), r.getTeammates(),
                        r.getSeatLabel(), r.getChallongeParticipantId() != null, r.getCreatedAt()))
                .toList();
        return new AdminView(t.getId(), t.getName(), t.getColor(), t.getFormatLabel(), t.getMaxParticipants(),
                t.getTeamSize(), t.isRegistrationOpen(), t.getRegistrationClosesAt(), t.getStartsAt(), t.getServerId(),
                t.getChallongeSlug(), t.getRulesUrl(), t.getSort(), t.getSnapshotAt(), regs);
    }

    private static void apply(Tournament t, TournamentRequest r) {
        t.setName(r.name().trim());
        t.setColor(r.color() == null || r.color().isBlank() ? "#9B5CFF" : r.color());
        t.setFormatLabel(blankToNull(r.formatLabel()));
        t.setMaxParticipants(r.maxParticipants());
        t.setTeamSize(r.teamSize());
        t.setRegistrationOpen(r.registrationOpen());
        t.setRegistrationClosesAt(r.registrationClosesAt());
        t.setStartsAt(r.startsAt());
        t.setServerId(r.serverId());
        t.setChallongeSlug(ChallongeClient.normalizeSlug(r.challongeSlug()));
        t.setRulesUrl(blankToNull(r.rulesUrl()));
        t.setSort(r.sort());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
