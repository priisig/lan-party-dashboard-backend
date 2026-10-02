package com.lanparty.dashboard.stats.provider;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.server.query.MinecraftPing;
import com.lanparty.dashboard.server.query.Rcon;
import com.lanparty.dashboard.server.query.ServerStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Minecraft Java server via Server List Ping (players, version, MOTD).
 * With RCON configured it also reads TPS (Paper/Spigot "tps") and the in-game day ("time query day").
 */
@Component
public class MinecraftProvider implements IntegrationProvider {

    private static final int TIMEOUT_MS = 3_000;
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    @Override
    public String type() {
        return "minecraft";
    }

    @Override
    public String label() {
        return "Minecraft Server";
    }

    @Override
    public List<ConfigField> configFields() {
        return List.of(
                ConfigField.text("host", "Host", true, "mc.vivolan.local"),
                ConfigField.number("port", "Port", false, "25565"),
                ConfigField.number("rconPort", "RCON-Port (optional, für TPS & Ingame-Tag)", false, "25575"),
                ConfigField.password("rconPassword", "RCON-Passwort"));
    }

    @Override
    public JsonNode fetch(JsonNode config) throws Exception {
        String host = Json.text(config, "host", "");
        if (host.isBlank()) {
            throw new IllegalArgumentException("Host fehlt");
        }
        int port = Json.integer(config, "port", 25565);
        ServerStatus status = MinecraftPing.query(host, port, TIMEOUT_MS);

        ObjectNode result = Json.object();
        result.put("address", port == 25565 ? host : host + ":" + port);
        result.put("online", status.playersOnline());
        result.put("max", status.maxPlayers());
        result.put("version", status.version());
        result.put("motd", status.motd());
        var players = result.putArray("players");
        status.players().forEach(players::add);

        String rconPassword = Json.text(config, "rconPassword", "");
        int rconPort = Json.integer(config, "rconPort", 0);
        if (rconPort > 0 && !rconPassword.isBlank()) {
            try (Rcon rcon = new Rcon(host, rconPort, rconPassword, TIMEOUT_MS)) {
                Double tps = firstNumber(rcon.command("tps"));
                if (tps != null) {
                    result.put("tps", tps);
                }
                Double day = firstNumber(rcon.command("time query day"));
                if (day != null) {
                    result.put("day", day.intValue());
                }
                Double daytime = firstNumber(rcon.command("time query daytime"));
                if (daytime != null) {
                    // 0 = sunrise, 6000 noon, 12000 sunset, 18000 midnight
                    result.put("isNight", daytime >= 12_542 && daytime < 23_460);
                }
            } catch (Exception e) {
                result.put("rconError", e.getMessage());
            }
        }
        return result;
    }

    /** First number after the last colon: "TPS from last 1m, 5m, 15m: 19.9, …" → 19.9, "The time is 37" → 37. */
    static Double firstNumber(String text) {
        String clean = MinecraftPing.stripFormatting(text);
        int colon = clean.lastIndexOf(':');
        Matcher m = NUMBER.matcher(colon >= 0 ? clean.substring(colon + 1) : clean);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }
}
