package com.charlie.ikibaho;

import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestFixtures.class})
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
    @Autowired
    private JdbcClient jdbc;

    /**
     * Truncate rather than @Transactional-rollback: these tests exercise flush
     * behavior (@Version, @SoftDelete, UPDATE ... RETURNING), and wrapping them
     * in a rolled-back transaction hides exactly the semantics under test.
     */
    @BeforeEach
    void resetDatabase() {
        List<String> tables = jdbc.sql("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename <> 'flyway_schema_history'
                """)
                .query(String.class)
                .list();

        if (!tables.isEmpty()) {
            jdbc.sql("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE")
                    .update();
        }
    }

    /**
     * SecurityContextHolder is a ThreadLocal and JUnit reuses threads. A leaked
     * authentication makes the next test pass for the wrong reason.
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
