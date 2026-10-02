package com.lanparty.dashboard.info;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;

@Service
public class InfoService {

    private final InfoItemRepository infos;
    private final ChangeNotifier notifier;

    public InfoService(InfoItemRepository infos, ChangeNotifier notifier) {
        this.infos = infos;
        this.notifier = notifier;
    }

    public record InfoDto(@NotBlank @Size(max = 100) String label, @NotBlank @Size(max = 500) String value) {
    }

    @Transactional(readOnly = true)
    public List<InfoDto> list(Event event) {
        return infos.findByEventIdOrderBySort(event.getId()).stream().map(i -> new InfoDto(i.getLabel(), i.getValue())).toList();
    }

    /** The admin edits the list as a whole; order in the request is the display order. */
    @Transactional
    public List<InfoDto> replace(Event event, List<InfoDto> items) {
        infos.deleteByEventId(event.getId());
        infos.flush();
        for (int i = 0; i < items.size(); i++) {
            infos.save(new InfoItem(event.getId(), items.get(i).label().trim(), items.get(i).value().trim(), i));
        }
        notifier.publish(Topic.EVENT);
        return list(event);
    }
}
