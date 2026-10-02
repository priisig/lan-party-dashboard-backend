package com.lanparty.dashboard.server.query;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Quake 3 engine "getstatus" (Call of Duty 1–4, Urban Terror, OpenArena, …). */
public final class Quake3Query {

    private Quake3Query() {
    }

    public static ServerStatus query(String host, int port, int timeoutMs) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            byte[] request = "ÿÿÿÿgetstatus\n".getBytes(StandardCharsets.ISO_8859_1);
            socket.send(new DatagramPacket(request, request.length, new InetSocketAddress(host, port)));
            byte[] buffer = new byte[16 * 1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            return parse(new String(buffer, 0, packet.getLength(), StandardCharsets.ISO_8859_1));
        }
    }

    static ServerStatus parse(String response) {
        String[] lines = response.split("\n");
        if (lines.length < 2 || !lines[0].endsWith("statusResponse")) {
            throw new IllegalStateException("Unexpected Quake3 response");
        }
        Map<String, String> vars = new HashMap<>();
        String[] parts = lines[1].split("\\\\");
        for (int i = 1; i + 1 < parts.length; i += 2) {
            vars.put(parts[i].toLowerCase(), parts[i + 1]);
        }
        List<String> players = new ArrayList<>();
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            int quote = line.indexOf('"');
            players.add(quote >= 0 ? stripColors(line.substring(quote + 1, line.lastIndexOf('"'))) : line);
        }
        Integer max = vars.containsKey("sv_maxclients") ? Integer.valueOf(vars.get("sv_maxclients")) : null;
        return new ServerStatus(true, players.size(), max, vars.get("mapname"), stripColors(vars.getOrDefault("sv_hostname", "")),
                vars.get("shortversion"), null, players, Instant.now(), null);
    }

    /** Removes Quake colour codes like ^1. */
    static String stripColors(String s) {
        return s.replaceAll("\\^.", "");
    }
}
