package com.lanparty.dashboard.server.query;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/** Valve A2S_INFO (Source / GoldSrc engines: CS2, GMod, TF2, …). */
public final class A2sQuery {

    private static final byte[] HEADER = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] PAYLOAD = "TSource Engine Query\0".getBytes(StandardCharsets.ISO_8859_1);

    private A2sQuery() {
    }

    public static ServerStatus query(String host, int port, int timeoutMs) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetSocketAddress address = new InetSocketAddress(host, port);
            byte[] response = exchange(socket, address, request(null));
            if (response.length >= 9 && response[4] == 0x41) {
                // S2C_CHALLENGE: repeat the request with the challenge number appended.
                byte[] challenge = new byte[4];
                System.arraycopy(response, 5, challenge, 0, 4);
                response = exchange(socket, address, request(challenge));
            }
            return parseInfo(response);
        }
    }

    static byte[] request(byte[] challenge) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER.length + PAYLOAD.length + (challenge == null ? 0 : 4));
        buffer.put(HEADER).put(PAYLOAD);
        if (challenge != null) {
            buffer.put(challenge);
        }
        return buffer.array();
    }

    static ServerStatus parseInfo(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt(); // 0xFFFFFFFF
        byte type = buf.get();
        if (type != 0x49) {
            throw new IllegalStateException("Unexpected A2S response type " + type);
        }
        buf.get(); // protocol
        String name = readString(buf);
        String map = readString(buf);
        readString(buf); // folder
        String game = readString(buf);
        buf.getShort(); // app id
        int players = Byte.toUnsignedInt(buf.get());
        int max = Byte.toUnsignedInt(buf.get());
        int bots = Byte.toUnsignedInt(buf.get());
        return new ServerStatus(true, Math.max(0, players - bots), max, map, name, game, null, List.of(), Instant.now(), null);
    }

    private static byte[] exchange(DatagramSocket socket, InetSocketAddress address, byte[] request) throws IOException {
        socket.send(new DatagramPacket(request, request.length, address));
        byte[] buffer = new byte[1400];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        byte[] result = new byte[packet.getLength()];
        System.arraycopy(buffer, 0, result, 0, packet.getLength());
        return result;
    }

    private static String readString(ByteBuffer buf) {
        int start = buf.position();
        while (buf.hasRemaining() && buf.get() != 0) {
            // advance to terminator
        }
        int end = buf.position() - 1;
        return new String(buf.array(), start, Math.max(0, end - start), StandardCharsets.UTF_8);
    }
}
