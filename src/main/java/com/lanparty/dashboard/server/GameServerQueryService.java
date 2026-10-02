package com.lanparty.dashboard.server;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.lanparty.dashboard.event.EventRepository;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.server.query.A2sQuery;
import com.lanparty.dashboard.server.query.MinecraftPing;
import com.lanparty.dashboard.server.query.Quake3Query;
import com.lanparty.dashboard.server.query.ServerStatus;

/** Periodically queries all game servers of the active event and keeps the latest status in memory. */
@Service
public class GameServerQueryService {

    private static final Logger log = LoggerFactory.getLogger(GameServerQueryService.class);
    private static final int TIMEOUT_MS = 2_000;

    private final GameServerRepository servers;
    private final EventRepository events;
    private final ChangeNotifier notifier;
    private final Clock clock;
    private final Map<Long, ServerStatus> statuses = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GameServerQueryService(GameServerRepository servers, EventRepository events, ChangeNotifier notifier, Clock clock) {
        this.servers = servers;
        this.events = events;
        this.notifier = notifier;
        this.clock = clock;
    }

    public ServerStatus status(Long serverId) {
        return statuses.get(serverId);
    }

    @Scheduled(initialDelay = 3_000, fixedDelayString = "${lan.server-query-ms:20000}")
    public void refresh() {
        events.findFirstByActiveTrue().ifPresent(event -> {
            List<GameServer> list = servers.findByEventIdOrderBySort(event.getId()).stream()
                    .filter(s -> s.getQueryType() != QueryType.NONE)
                    .toList();
            List<Future<Boolean>> results = list.stream().map(s -> executor.submit(() -> update(s))).toList();
            boolean changed = false;
            for (Future<Boolean> f : results) {
                try {
                    changed |= f.get();
                } catch (Exception e) {
                    log.debug("Server query failed", e);
                }
            }
            if (changed) {
                notifier.publish(Topic.SERVERS);
                notifier.publish(Topic.STATS);
            }
        });
    }

    private boolean update(GameServer server) {
        ServerStatus next = query(server);
        ServerStatus previous = statuses.put(server.getId(), next);
        return previous == null || previous.online() != next.online()
                || !Objects.equals(previous.playersOnline(), next.playersOnline())
                || !Objects.equals(previous.maxPlayers(), next.maxPlayers())
                || !Objects.equals(previous.map(), next.map());
    }

    public ServerStatus query(GameServer server) {
        try {
            int port = server.effectiveQueryPort();
            return switch (server.getQueryType()) {
                case SOURCE -> A2sQuery.query(server.getHost(), port, TIMEOUT_MS);
                case QUAKE3 -> Quake3Query.query(server.getHost(), port, TIMEOUT_MS);
                case MINECRAFT -> MinecraftPing.query(server.getHost(), port, TIMEOUT_MS);
                case NONE -> null;
            };
        } catch (Exception e) {
            return ServerStatus.offline(e.getClass().getSimpleName() + ": " + e.getMessage(), clock.instant());
        }
    }
}
