package com.lanparty.dashboard.seating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.NotFoundException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.event.EventRepository;
import com.lanparty.dashboard.event.SeatRules;
import com.lanparty.dashboard.user.AppUser;
import com.lanparty.dashboard.user.AppUserRepository;
import com.lanparty.dashboard.user.Participant;
import com.lanparty.dashboard.user.ParticipantRepository;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.seating.SeatingDtos.AssignRequest;
import com.lanparty.dashboard.seating.SeatingDtos.LayoutRequest;
import com.lanparty.dashboard.seating.SeatingDtos.MarkerLayout;
import com.lanparty.dashboard.seating.SeatingDtos.MarkerView;
import com.lanparty.dashboard.seating.SeatingDtos.PendingRequest;
import com.lanparty.dashboard.seating.SeatingDtos.ReservationRequest;
import com.lanparty.dashboard.seating.SeatingDtos.RowLayout;
import com.lanparty.dashboard.seating.SeatingDtos.RowView;
import com.lanparty.dashboard.seating.SeatingDtos.SeatMapView;
import com.lanparty.dashboard.seating.SeatingDtos.SeatView;

@Service
public class SeatingService {

    private final SeatRowRepository rows;
    private final SeatRepository seats;
    private final SeatRequestRepository requests;
    private final RoomMarkerRepository markers;
    private final AppUserRepository users;
    private final ParticipantRepository participants;
    private final EventRepository events;
    private final ChangeNotifier notifier;

    public SeatingService(SeatRowRepository rows, SeatRepository seats, SeatRequestRepository requests,
                          RoomMarkerRepository markers, AppUserRepository users, ParticipantRepository participants,
                          EventRepository events, ChangeNotifier notifier) {
        this.rows = rows;
        this.seats = seats;
        this.requests = requests;
        this.markers = markers;
        this.users = users;
        this.participants = participants;
        this.events = events;
        this.notifier = notifier;
    }

    /** @param includeNotes notes are internal and only shown to admins */
    @Transactional(readOnly = true)
    public SeatMapView map(Event event, boolean includeNotes) {
        List<SeatRow> rowList = rows.findByEventIdOrderBySort(event.getId());
        List<Seat> seatList = seats.findByEventId(event.getId());
        Set<Long> pendingSeatIds = seatList.isEmpty() ? Set.of()
                : requests.findBySeatIdInAndStatusOrderByCreatedAt(seatList.stream().map(Seat::getId).toList(), RequestStatus.PENDING)
                .stream().map(SeatRequest::getSeatId).collect(Collectors.toSet());
        Map<Long, List<Seat>> byRow = seatList.stream().collect(Collectors.groupingBy(Seat::getRowId));
        Map<Long, AppUser> people = users.findByIdIn(seatList.stream().map(Seat::getUserId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, u -> u));

        int taken = 0;
        int free = 0;
        int blocked = 0;
        List<RowView> rowViews = new ArrayList<>();
        for (SeatRow row : rowList) {
            List<SeatView> seatViews = new ArrayList<>();
            for (Seat s : byRow.getOrDefault(row.getId(), List.of()).stream().sorted((a, b) -> Integer.compare(a.getNumber(), b.getNumber())).toList()) {
                switch (s.getStatus()) {
                    case TAKEN -> taken++;
                    case FREE -> free++;
                    case BLOCKED -> blocked++;
                }
                seatViews.add(new SeatView(s.getLabel(), s.getNumber(), s.getStatus(), displayName(s, people, includeNotes),
                        pendingSeatIds.contains(s.getId()), includeNotes ? s.getNote() : null));
            }
            rowViews.add(new RowView(row.getId(), row.getLabel(), seatViews));
        }
        List<MarkerView> markerViews = markers.findByEventIdOrderBySort(event.getId()).stream().map(MarkerView::of).toList();
        return new SeatMapView(event.getSeatOrientation(), event.isSeatRowsReversed(), event.isSeatNumbersReversed(),
                markerViews, rowViews, taken, free, blocked, taken + free + blocked);
    }

    /** Current account name; hidden on the public map when the user doesn't want to be shown. */
    private static String displayName(Seat s, Map<Long, AppUser> people, boolean admin) {
        if (s.getUserId() == null) {
            return s.getGamertag();
        }
        AppUser u = people.get(s.getUserId());
        if (u == null) {
            return s.getGamertag();
        }
        return admin || u.isShowOnSeatmap() ? u.getNickname() : null;
    }

    // ------------------------------------------------------------------ participant self-service

    public record MySeat(String seat, String pending) {
    }

    @Transactional(readOnly = true)
    public MySeat mySeat(Event event, Long userId) {
        String seat = seats.findFirstByEventIdAndUserId(event.getId(), userId).map(Seat::getLabel).orElse(null);
        String pending = myPending(event, userId).stream().findFirst()
                .flatMap(r -> seats.findById(r.getSeatId())).map(Seat::getLabel).orElse(null);
        return new MySeat(seat, pending);
    }

    /**
     * Books a seat for the logged-in user, or files a request when the event requires approval.
     * A user with a seat moves to the new one (if changes are allowed); the old seat is freed.
     */
    @Transactional
    public void reserve(Event event, AppUser user, String label, ReservationRequest request) {
        SeatRules rules = event.getSeatRules();
        if (!rules.isSeatSelectionOpen()) {
            throw new BadRequestException("Die Platzwahl ist geschlossen – bitte wende dich an die Orga.");
        }
        Seat target = seat(event, label);
        Seat current = seats.findFirstByEventIdAndUserId(event.getId(), user.getId()).orElse(null);
        if (current != null && current.getId().equals(target.getId())) {
            return;
        }
        if (current != null && !rules.isSeatChangeAllowed()) {
            throw new BadRequestException("Platzwechsel sind gerade nicht möglich – bitte wende dich an die Orga.");
        }
        if (target.getStatus() != SeatStatus.FREE) {
            throw new BadRequestException("Platz " + target.getLabel() + " ist nicht frei.");
        }
        boolean othersWaiting = requests.findBySeatIdInAndStatusOrderByCreatedAt(List.of(target.getId()), RequestStatus.PENDING).stream()
                .anyMatch(r -> !user.getId().equals(r.getUserId()));
        if (othersWaiting) {
            throw new BadRequestException("Für Platz " + target.getLabel() + " liegt bereits eine Reservation vor.");
        }
        // Only one open request per user.
        myPending(event, user.getId()).forEach(r -> r.setStatus(RequestStatus.REJECTED));
        if (rules.isSeatApprovalRequired()) {
            requests.save(new SeatRequest(target.getId(), user.getNickname(), user.getId(),
                    request == null ? null : blankToNull(request.companions())));
        } else {
            if (current != null) {
                current.release();
            }
            target.assign(user.getNickname(), user.getId());
        }
        notifier.publish(Topic.SEATS);
    }

    /** Gives up the own seat and withdraws open requests. */
    @Transactional
    public void cancel(Event event, Long userId) {
        myPending(event, userId).forEach(r -> r.setStatus(RequestStatus.REJECTED));
        seats.findFirstByEventIdAndUserId(event.getId(), userId).ifPresent(Seat::release);
        notifier.publish(Topic.SEATS);
    }

    private List<SeatRequest> myPending(Event event, Long userId) {
        Set<Long> eventSeats = seats.findByEventId(event.getId()).stream().map(Seat::getId).collect(Collectors.toSet());
        return requests.findByUserIdAndStatus(userId, RequestStatus.PENDING).stream()
                .filter(r -> eventSeats.contains(r.getSeatId()))
                .toList();
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<PendingRequest> pending(Event event) {
        Map<Long, Seat> seatById = seats.findByEventId(event.getId()).stream().collect(Collectors.toMap(Seat::getId, s -> s));
        if (seatById.isEmpty()) {
            return List.of();
        }
        return requests.findBySeatIdInAndStatusOrderByCreatedAt(seatById.keySet(), RequestStatus.PENDING).stream()
                .map(r -> new PendingRequest(r.getId(), seatById.get(r.getSeatId()).getLabel(), r.getGamertag(), r.getCompanions(), r.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void approve(Event event, Long requestId) {
        SeatRequest request = request(event, requestId);
        Seat seat = seats.findById(request.getSeatId()).orElseThrow();
        String name = request.getUserId() == null ? request.getGamertag()
                : users.findById(request.getUserId()).map(AppUser::getNickname).orElse(request.getGamertag());
        assignInternal(event, seat, name, request.getUserId());
        request.setStatus(RequestStatus.APPROVED);
        rejectOthers(seat.getId(), requestId);
        notifier.publish(Topic.SEATS);
    }

    @Transactional
    public void reject(Event event, Long requestId) {
        request(event, requestId).setStatus(RequestStatus.REJECTED);
        notifier.publish(Topic.SEATS);
    }

    /** Assigns a gamertag (moving the player if seated elsewhere) or releases the seat when blank. */
    @Transactional
    public void assign(Event event, String label, AssignRequest request) {
        Seat seat = seat(event, label);
        seat.setNote(blankToNull(request.note()));
        String gamertag = blankToNull(request.gamertag());
        if (gamertag == null) {
            if (seat.getStatus() == SeatStatus.TAKEN) {
                seat.release();
            }
        } else {
            // Typing an account's nickname links the seat to that account.
            Long userId = users.findByNicknameIgnoreCase(gamertag).map(AppUser::getId).orElse(null);
            assignInternal(event, seat, gamertag, userId);
            rejectOthers(seat.getId(), null);
        }
        notifier.publish(Topic.SEATS);
    }

    @Transactional
    public void block(Event event, String label) {
        Seat seat = seat(event, label);
        seat.block();
        rejectOthers(seat.getId(), null);
        notifier.publish(Topic.SEATS);
    }

    @Transactional
    public void release(Event event, String label) {
        seat(event, label).release();
        notifier.publish(Topic.SEATS);
    }

    /**
     * Applies a new layout. Seats keep their assignment as long as their label (row + number) still exists,
     * so shrinking a row only drops the seats at its end.
     */
    @Transactional
    public void updateLayout(Event event, LayoutRequest request) {
        Set<String> labels = new HashSet<>();
        for (RowLayout r : request.rows()) {
            if (!labels.add(r.label().toUpperCase())) {
                throw new BadRequestException("Reihe «" + r.label() + "» kommt doppelt vor.");
            }
        }
        Event managed = events.findById(event.getId()).orElseThrow();
        managed.setSeatOrientation(request.orientation());
        managed.setSeatRowsReversed(request.rowsReversed());
        managed.setSeatNumbersReversed(request.numbersReversed());
        markers.deleteByEventId(event.getId());
        int markerSort = 0;
        for (MarkerLayout m : request.markers()) {
            markers.save(new RoomMarker(event.getId(), m.kind(), m.label().trim(), m.side(), m.align(), markerSort++));
        }

        Map<Long, SeatRow> existingRows = new HashMap<>();
        rows.findByEventIdOrderBySort(event.getId()).forEach(r -> existingRows.put(r.getId(), r));
        Map<String, Seat> existingSeats = new HashMap<>();
        seats.findByEventId(event.getId()).forEach(s -> existingSeats.put(s.getLabel().toUpperCase(), s));

        Set<String> keptLabels = new HashSet<>();
        int sort = 0;
        for (RowLayout layout : request.rows()) {
            String rowLabel = layout.label().toUpperCase();
            SeatRow row = layout.id() != null ? existingRows.remove(layout.id()) : null;
            if (row == null) {
                row = rows.save(new SeatRow(event.getId(), rowLabel, layout.seatCount(), sort));
            } else {
                row.setLabel(rowLabel);
                row.setSeatCount(layout.seatCount());
                row.setSort(sort);
            }
            sort++;
            for (int n = 1; n <= layout.seatCount(); n++) {
                String label = rowLabel + n;
                keptLabels.add(label);
                Seat seat = existingSeats.get(label);
                if (seat == null) {
                    seats.save(new Seat(event.getId(), row.getId(), n, label));
                } else {
                    seat.setRowId(row.getId());
                }
            }
        }
        existingSeats.forEach((label, seat) -> {
            if (!keptLabels.contains(label)) {
                seats.delete(seat);
            }
        });
        rows.deleteAll(existingRows.values());
        notifier.publish(Topic.SEATS);
    }

    /** Copies orientation and room markers from one event to another (clone). */
    @Transactional
    public void copyRoom(Event source, Event target) {
        target.setSeatOrientation(source.getSeatOrientation());
        target.setSeatRowsReversed(source.isSeatRowsReversed());
        target.setSeatNumbersReversed(source.isSeatNumbersReversed());
        markers.findByEventIdOrderBySort(source.getId()).forEach(m -> markers.save(m.copyTo(target.getId())));
    }

    /** Creates a fresh layout with free seats, used for new events and clones. */
    @Transactional
    public void createLayout(Long eventId, List<SeatRow> template, Set<String> blockedLabels) {
        for (SeatRow t : template) {
            SeatRow row = rows.save(new SeatRow(eventId, t.getLabel(), t.getSeatCount(), t.getSort()));
            for (int n = 1; n <= t.getSeatCount(); n++) {
                Seat seat = new Seat(eventId, row.getId(), n, row.getLabel() + n);
                if (blockedLabels.contains(seat.getLabel())) {
                    seat.block();
                }
                seats.save(seat);
            }
        }
    }

    private void assignInternal(Event event, Seat seat, String gamertag, Long userId) {
        if (seat.getStatus() == SeatStatus.BLOCKED) {
            throw new BadRequestException("Platz " + seat.getLabel() + " ist gesperrt – zuerst freigeben.");
        }
        for (Seat other : seats.findByEventId(event.getId())) {
            boolean same = userId != null ? userId.equals(other.getUserId()) : gamertag.equalsIgnoreCase(other.getGamertag());
            if (!other.getId().equals(seat.getId()) && same) {
                other.release();
            }
        }
        seat.assign(gamertag, userId);
        if (userId != null) {
            participants.findByEventIdAndUserId(event.getId(), userId)
                    .orElseGet(() -> participants.save(new Participant(event.getId(), userId)));
        }
    }

    private void rejectOthers(Long seatId, Long exceptRequestId) {
        requests.findBySeatIdInAndStatusOrderByCreatedAt(List.of(seatId), RequestStatus.PENDING).stream()
                .filter(r -> !r.getId().equals(exceptRequestId))
                .forEach(r -> r.setStatus(RequestStatus.REJECTED));
    }

    private Seat seat(Event event, String label) {
        return seats.findByEventIdAndLabelIgnoreCase(event.getId(), label)
                .orElseThrow(() -> new NotFoundException("Platz " + label + " gibt es nicht."));
    }

    private SeatRequest request(Event event, Long id) {
        SeatRequest request = requests.findById(id).orElseThrow(() -> new NotFoundException("Reservation nicht gefunden."));
        Seat seat = seats.findById(request.getSeatId()).orElseThrow();
        if (!seat.getEventId().equals(event.getId())) {
            throw new NotFoundException("Reservation nicht gefunden.");
        }
        return request;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
