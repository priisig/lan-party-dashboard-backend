package com.lanparty.dashboard.server;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.server.query.ServerStatus;

@Service
public class GameServerService {

    private final GameServerRepository servers;
    private final GameServerQueryService queries;
    private final ChangeNotifier notifier;
    private final Clock clock;

    public GameServerService(GameServerRepository servers, GameServerQueryService queries, ChangeNotifier notifier, Clock clock) {
        this.servers = servers;
        this.queries = queries;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Public card entry. {@code availableFrom} set and in the future → shown as "ab 22:00". */
    public record PublicServer(Long id, String name, String shortCode, String address, String connectUrl,
                               boolean online, Integer players, Integer maxPlayers, String map, Instant availableFrom) {
    }

    public record ServerDto(Long id, @NotBlank @Size(max = 120) String name, @Size(max = 30) String shortCode,
                            @NotBlank @Size(max = 200) String host, @Min(1) @Max(65535) Integer port,
                            @Min(1) @Max(65535) Integer queryPort, @NotNull QueryType queryType,
                            @Size(max = 300) String connectUrl, boolean visible, Instant availableFrom) {
    }

    @Transactional(readOnly = true)
    public List<PublicServer> publicList(Event event) {
        Instant now = clock.instant();
        return servers.findByEventIdOrderBySort(event.getId()).stream()
                .filter(GameServer::isVisible)
                .map(s -> {
                    ServerStatus st = queries.status(s.getId());
                    boolean upcoming = s.getAvailableFrom() != null && s.getAvailableFrom().isAfter(now);
                    boolean online = !upcoming && (s.getQueryType() == QueryType.NONE || (st != null && st.online()));
                    return new PublicServer(s.getId(), s.getName(), s.getShortCode(), s.address(), s.effectiveConnectUrl(),
                            online, st == null || upcoming ? null : st.playersOnline(), st == null ? null : st.maxPlayers(),
                            st == null ? null : st.map(), upcoming ? s.getAvailableFrom() : null);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServerDto> list(Event event) {
        return servers.findByEventIdOrderBySort(event.getId()).stream()
                .map(s -> new ServerDto(s.getId(), s.getName(), s.getShortCode(), s.getHost(), s.getPort(), s.getQueryPort(),
                        s.getQueryType(), s.getConnectUrl(), s.isVisible(), s.getAvailableFrom()))
                .toList();
    }

    /** Syncs the list by id: existing servers are updated (keeping references from tournaments), new ones created, missing ones deleted. */
    @Transactional
    public List<ServerDto> replace(Event event, List<ServerDto> items) {
        Map<Long, GameServer> existing = new HashMap<>();
        servers.findByEventIdOrderBySort(event.getId()).forEach(s -> existing.put(s.getId(), s));
        for (int i = 0; i < items.size(); i++) {
            ServerDto dto = items.get(i);
            GameServer s = dto.id() == null ? null : existing.remove(dto.id());
            if (s == null) {
                s = new GameServer();
                s.setEventId(event.getId());
            }
            s.setName(dto.name().trim());
            s.setShortCode(blankToNull(dto.shortCode()));
            s.setHost(dto.host().trim());
            s.setPort(dto.port());
            s.setQueryPort(dto.queryPort());
            s.setQueryType(dto.queryType());
            s.setConnectUrl(blankToNull(dto.connectUrl()));
            s.setVisible(dto.visible());
            s.setAvailableFrom(dto.availableFrom());
            s.setSort(i);
            servers.save(s);
        }
        servers.deleteAll(existing.values());
        notifier.publish(Topic.SERVERS);
        return list(event);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
