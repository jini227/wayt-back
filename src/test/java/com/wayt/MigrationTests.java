package com.wayt;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationTests {
    @Test
    void inviteCancelledStatusMigrationAllowsExistingH2EnumColumnToStoreCancelled() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:invite-cancelled-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        ); var statement = connection.createStatement()) {
            statement.execute("create table invites (status enum('ACCEPTED', 'DECLINED', 'EXPIRED', 'PENDING') not null)");
            assertThatThrownBy(() -> statement.executeUpdate("insert into invites(status) values('CANCELLED')"))
                    .isInstanceOf(SQLException.class);

            executeSqlFile(statement, "src/main/resources/db/migration/V8__invite_cancelled_status.sql");

            statement.executeUpdate("insert into invites(status) values('CANCELLED')");
            try (var result = statement.executeQuery("select status from invites")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("CANCELLED");
            }
        }
    }

    private void executeSqlFile(Statement statement, String path) throws Exception {
        String migration = Files.readString(Path.of(path));
        for (String sql : migration.split(";")) {
            if (!sql.isBlank()) {
                statement.execute(sql);
            }
        }
    }
}
