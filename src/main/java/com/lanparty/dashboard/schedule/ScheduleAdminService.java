package com.lanparty.dashboard.schedule;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;

@Service
public class ScheduleAdminService {

    private final ScheduleItemRepository items;
    private final ChangeNotifier notifier;

    public ScheduleAdminService(ScheduleItemRepository items, ChangeNotifier notifier) {
        this.items = items;
        this.notifier = notifier;
    }

    public record ScheduleDto(Long id, @NotNull Instant startsAt, Instant endsAt, @NotBlank @Size(max = 200) String title,
                              @Size(max = 100) String location, @Size(max = 20) String color, Long tournamentId) {
    }

    @Transactional(readOnly = true)
    public List<ScheduleDto> list(Event event) {
        return items.findByEventIdOrderByStartsAt(event.getId()).stream()
                .map(i -> new ScheduleDto(i.getId(), i.getStartsAt(), i.getEndsAt(), i.getTitle(), i.getLocation(), i.getColor(), i.getTournamentId()))
                .toList();
    }

    @Transactional
    public List<ScheduleDto> replace(Event event, List<ScheduleDto> list) {
        for (ScheduleDto d : list) {
            if (d.endsAt() != null && !d.endsAt().isAfter(d.startsAt())) {
                throw new BadRequestException("«" + d.title() + "»: Ende muss nach dem Start liegen.");
            }
        }
        items.deleteByEventId(event.getId());
        items.flush();
        for (ScheduleDto d : list) {
            String color = d.color() == null || d.color().isBlank() ? "#6B6390" : d.color();
            items.save(new ScheduleItem(event.getId(), d.startsAt(), d.endsAt(), d.title().trim(),
                    d.location() == null || d.location().isBlank() ? null : d.location().trim(), color, d.tournamentId()));
        }
        notifier.publish(Topic.SCHEDULE);
        return list(event);
    }
}
