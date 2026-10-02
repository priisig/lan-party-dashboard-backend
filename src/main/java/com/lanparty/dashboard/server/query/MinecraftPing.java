package com.lanparty.dashboard.server.query;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.lanparty.dashboard.common.Json;

import tools.jackson.databind.JsonNode;

/** Minecraft Java Edition Server List Ping (protocol used by the multiplayer server list). */
public final class MinecraftPing {

    private MinecraftPing() {
    }

    public static ServerStatus query(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(handshake);
            writeVarInt(h, 0x00);      // packet id: handshake
            writeVarInt(h, -1);        // protocol version: -1 = "just tell me"
            writeString(h, host);
            h.writeShort(port);
            writeVarInt(h, 1);         // next state: status
            writePacket(out, handshake.toByteArray());
            writePacket(out, new byte[] {0x00}); // status request

            readVarInt(in); // packet length
            int id = readVarInt(in);
            if (id != 0x00) {
                throw new IOException("Unexpected Minecraft packet id " + id);
            }
            int length = readVarInt(in);
            byte[] json = new byte[length];
            in.readFully(json);
            return parse(new String(json, StandardCharsets.UTF_8));
        }
    }

    static ServerStatus parse(String json) {
        JsonNode root = Json.parse(json);
        JsonNode players = root.path("players");
        List<String> names = new ArrayList<>();
        for (JsonNode p : players.path("sample")) {
            String name = p.path("name").asString("");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return new ServerStatus(true, players.path("online").asInt(), players.path("max").asInt(), null, null,
                root.path("version").path("name").asString(null), motd(root.path("description")), names, Instant.now(), null);
    }

    /** The MOTD is either a plain string or a chat component tree. */
    static String motd(JsonNode description) {
        if (description.isString()) {
            return stripFormatting(description.asString());
        }
        StringBuilder text = new StringBuilder(description.path("text").asString(""));
        for (JsonNode extra : description.path("extra")) {
            text.append(motd(extra));
        }
        return stripFormatting(text.toString());
    }

    public static String stripFormatting(String s) {
        return s.replaceAll("§.", "").trim();
    }

    private static void writePacket(OutputStream out, byte[] data) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(packet);
        writeVarInt(d, data.length);
        d.write(data);
        out.write(packet.toByteArray());
        out.flush();
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("Connection closed");
            }
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 32) {
                throw new IOException("VarInt too big");
            }
        }
    }
}
