package com.lanparty.dashboard.server.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class QueryParsersTest {

    @Test
    void parsesA2sInfoAndSubtractsBots() {
        ByteBuffer b = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(-1).put((byte) 0x49).put((byte) 17);
        for (String s : new String[] {"VIVO CS2", "de_dust2", "csgo", "Counter-Strike 2"}) {
            b.put(s.getBytes(StandardCharsets.UTF_8)).put((byte) 0);
        }
        b.putShort((short) 730).put((byte) 12).put((byte) 16).put((byte) 2);
        byte[] data = new byte[b.position()];
        b.flip().get(data);

        ServerStatus status = A2sQuery.parseInfo(data);
        assertThat(status.serverName()).isEqualTo("VIVO CS2");
        assertThat(status.map()).isEqualTo("de_dust2");
        assertThat(status.playersOnline()).isEqualTo(10);
        assertThat(status.maxPlayers()).isEqualTo(16);
    }

    @Test
    void parsesQuake3Status() {
        String response = "ÿÿÿÿstatusResponse\n"
                + "\\sv_hostname\\^1VIVO ^7Promod\\mapname\\mp_crash\\sv_maxclients\\12\n"
                + "0 25 \"^2Lag^7Legend\"\n"
                + "3 40 \"RushB\"\n";
        ServerStatus status = Quake3Query.parse(response);
        assertThat(status.serverName()).isEqualTo("VIVO Promod");
        assertThat(status.playersOnline()).isEqualTo(2);
        assertThat(status.maxPlayers()).isEqualTo(12);
        assertThat(status.players()).containsExactly("LagLegend", "RushB");
        assertThat(status.map()).isEqualTo("mp_crash");
    }

    @Test
    void parsesMinecraftStatusWithChatComponentMotd() {
        String json = """
                {"version":{"name":"Paper 1.21.8","protocol":772},
                 "players":{"max":40,"online":2,"sample":[{"name":"Creeper_Kai","id":"x"},{"name":"BlockBen","id":"y"}]},
                 "description":{"text":"§aWillkommen ","extra":[{"text":"auf VIVO"}]}}
                """;
        ServerStatus status = MinecraftPing.parse(json);
        assertThat(status.playersOnline()).isEqualTo(2);
        assertThat(status.maxPlayers()).isEqualTo(40);
        assertThat(status.version()).isEqualTo("Paper 1.21.8");
        assertThat(status.motd()).isEqualTo("Willkommen auf VIVO");
        assertThat(status.players()).containsExactly("Creeper_Kai", "BlockBen");
        assertThat(MinecraftPing.parse("{\"description\":\"§6Hi\",\"players\":{}}").motd()).isEqualTo("Hi");
    }

    @Test
    void varIntRoundTrip() throws IOException {
        for (int value : new int[] {0, 1, 127, 128, 25565, -1}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            MinecraftPing.writeVarInt(new DataOutputStream(bytes), value);
            assertThat(MinecraftPing.readVarInt(new ByteArrayInputStream(bytes.toByteArray()))).isEqualTo(value);
        }
    }
}
