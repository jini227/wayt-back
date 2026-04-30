package com.wayt.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class H2TravelModeSchemaRepair implements ApplicationRunner {
    private static final List<TravelModeColumn> TRAVEL_MODE_COLUMNS = List.of(
            new TravelModeColumn("participants", "travel_mode"),
            new TravelModeColumn("user_accounts", "default_travel_mode")
    );

    private final JdbcTemplate jdbcTemplate;

    public H2TravelModeSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isH2Database()) {
            return;
        }

        TRAVEL_MODE_COLUMNS.forEach(this::dropOldTravelModeConstraints);
    }

    private boolean isH2Database() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData()
                        .getDatabaseProductName()
                        .toLowerCase(Locale.ROOT)
                        .contains("h2")));
    }

    private void dropOldTravelModeConstraints(TravelModeColumn column) {
        List<String> constraintNames = jdbcTemplate.query("""
                select cc.constraint_name
                from information_schema.check_constraints cc
                join information_schema.table_constraints tc
                  on cc.constraint_catalog = tc.constraint_catalog
                 and cc.constraint_schema = tc.constraint_schema
                 and cc.constraint_name = tc.constraint_name
                where upper(tc.table_name) = ?
                  and upper(cc.check_clause) like ?
                  and upper(cc.check_clause) not like '%BICYCLE%'
                """,
                (rs, rowNum) -> rs.getString(1),
                column.tableName().toUpperCase(Locale.ROOT),
                "%" + column.columnName().toUpperCase(Locale.ROOT) + "%");

        for (String constraintName : constraintNames) {
            jdbcTemplate.execute("alter table " + column.tableName()
                    + " drop constraint " + quoteIdentifier(constraintName));
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record TravelModeColumn(String tableName, String columnName) {
    }
}
