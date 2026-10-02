package com.lanparty.dashboard.server.query;

import java.time.Instant;
import java.util.List;

/** Result of querying a game server. {@code players} may be empty when the protocol doesn't list names. */
public record ServerStatus(boolean online, Integer playersOnline, Integer maxPlayers, String map, String serverName,
                           String version, String motd, List<String> players, Instant checkedAt, String error) {

    public static ServerStatus offline(String error, Instant now) {
        return new ServerStatus(false, null, null, null, null, null, null, List.of(), now, error);
    }
}
