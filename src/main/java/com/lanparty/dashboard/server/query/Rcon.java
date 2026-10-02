package com.lanparty.dashboard.server.query;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal Source/Minecraft RCON client for read-only commands (TPS, in-game day…). */
public final class Rcon implements AutoCloseable {

    private static final int LOGIN = 3;
    private static final int COMMAND = 2;

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextId = 1;

    public Rcon(String host, int port, String password, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        int id = send(LOGIN, password);
        if (readResponseId() != id) {
            close();
            throw new IOException("RCON-Login fehlgeschlagen");
        }
    }

    public String command(String command) throws IOException {
        send(COMMAND, command);
        ByteBuffer packet = readPacket();
        packet.getInt(); // id
        packet.getInt(); // type
        byte[] body = new byte[packet.remaining() - 2];
        packet.get(body);
        return new String(body, StandardCharsets.UTF_8);
    }

    private int send(int type, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        int id = nextId++;
        ByteBuffer packet = ByteBuffer.allocate(4 + 4 + 4 + body.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(4 + 4 + body.length + 2).putInt(id).putInt(type).put(body).put((byte) 0).put((byte) 0);
        out.write(packet.array());
        out.flush();
        return id;
    }

    private int readResponseId() throws IOException {
        return readPacket().getInt();
    }

    private ByteBuffer readPacket() throws IOException {
        byte[] lengthBytes = new byte[4];
        in.readFully(lengthBytes);
        int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length < 10 || length > 1 << 20) {
            throw new IOException("Invalid RCON packet length " + length);
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
