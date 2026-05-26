package com.noos.backend.mobile.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MobileSchemaTest {

    private static final List<String> MOBILE_TABLES = List.of(
            "mobile_sessions",
            "generated_audio",
            "state_measurements",
            "session_feedback",
            "lighting_jobs",
            "idempotency_keys",
            "push_devices"
    );

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void all_mobile_tables_exist() {
        for (String table : MOBILE_TABLES) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """, Integer.class, table);

            assertThat(count)
                    .as("table %s should exist", table)
                    .isEqualTo(1);
        }
    }
}
