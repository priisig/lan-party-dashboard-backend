package com.lanparty.dashboard.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V2 turns the old beamer side + edge labels into orientation and room markers without changing the picture. */
@Testcontainers
class SeatingMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void sideBeamerBecomesColumnsWithMarkers() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("1").load().migrate();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = c.createStatement()) {
            st.execute("insert into event (slug, title, starts_at, ends_at, beamer_side, seat_label_start, seat_label_end) "
                    + "values ('a', 'A', now(), now(), 'RIGHT', 'Eingang', 'Theke'), ('b', 'B', now(), now(), 'TOP', null, null)");

            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();

            ResultSet a = st.executeQuery("select seat_orientation, seat_rows_reversed from event where slug = 'a'");
            a.next();
            assertThat(a.getString(1)).isEqualTo("COLUMNS");
            assertThat(a.getBoolean(2)).isTrue();
            ResultSet markers = st.executeQuery("select m.kind, m.label, m.side from room_marker m join event e on e.id = m.event_id "
                    + "where e.slug = 'a' order by m.sort");
            markers.next();
            assertThat(markers.getString(1) + markers.getString(3)).isEqualTo("BEAMERRIGHT");
            markers.next();
            assertThat(markers.getString(2) + markers.getString(3)).isEqualTo("EingangTOP");
            markers.next();
            assertThat(markers.getString(2) + markers.getString(3)).isEqualTo("ThekeBOTTOM");
            ResultSet b = st.executeQuery("select seat_orientation, seat_rows_reversed from event where slug = 'b'");
            b.next();
            assertThat(b.getString(1)).isEqualTo("ROWS");
            assertThat(b.getBoolean(2)).isFalse();
        }
    }
}
