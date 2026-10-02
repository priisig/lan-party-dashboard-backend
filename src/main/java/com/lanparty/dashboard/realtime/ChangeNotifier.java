package com.lanparty.dashboard.realtime;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes "something changed" hints to all open dashboards via Server-Sent Events.
 * Clients re-fetch the affected data, so payloads stay tiny and nothing can get out of sync.
 */
@Component
public class ChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(ChangeNotifier.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        send(emitter, SseEmitter.event().name("hello").data("{}"));
        return emitter;
    }

    /** Notifies clients that {@code topic} changed; deferred until commit when called inside a transaction. */
    public void publish(Topic topic) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(topic);
                }
            });
        } else {
            broadcast(topic);
        }
    }

    private void broadcast(Topic topic) {
        String data = "{\"topic\":\"" + topic.key() + "\"}";
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name("change").data(data));
        }
    }

    /** Keeps proxies (nginx) from closing idle connections. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping SSE client: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }

    public int clientCount() {
        return emitters.size();
    }
}
