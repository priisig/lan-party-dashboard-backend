package com.lanparty.dashboard.announcement;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcements;
    private final ChangeNotifier notifier;

    public AnnouncementService(AnnouncementRepository announcements, ChangeNotifier notifier) {
        this.announcements = announcements;
        this.notifier = notifier;
    }

    public record AnnouncementDto(Long id, @NotBlank @Size(max = 500) String text, boolean enabled, Instant startsAt, Instant endsAt) {
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> list(Event event) {
        return announcements.findByEventIdOrderBySort(event.getId()).stream()
                .map(a -> new AnnouncementDto(a.getId(), a.getText(), a.isEnabled(), a.getStartsAt(), a.getEndsAt()))
                .toList();
    }

    @Transactional
    public List<AnnouncementDto> replace(Event event, List<AnnouncementDto> items) {
        for (AnnouncementDto a : items) {
            if (a.startsAt() != null && a.endsAt() != null && !a.endsAt().isAfter(a.startsAt())) {
                throw new BadRequestException("Durchsage «" + a.text() + "»: Ende muss nach dem Start liegen.");
            }
        }
        announcements.deleteByEventId(event.getId());
        announcements.flush();
        for (int i = 0; i < items.size(); i++) {
            AnnouncementDto a = items.get(i);
            announcements.save(new Announcement(event.getId(), a.text().trim(), a.enabled(), a.startsAt(), a.endsAt(), i));
        }
        notifier.publish(Topic.BANNERS);
        return list(event);
    }
}
