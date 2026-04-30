package com.wayt.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:h2_travel_mode_repair;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.flyway.enabled=false"
})
@Import(H2TravelModeSchemaRepair.class)
class H2TravelModeSchemaRepairTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private H2TravelModeSchemaRepair repair;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists participants");
        jdbcTemplate.execute("drop table if exists user_accounts");
        jdbcTemplate.execute("""
                create table participants (
                    id varchar(36) primary key,
                    travel_mode varchar(20) not null,
                    constraint participants_travel_mode_check
                        check (travel_mode in ('CAR', 'TRANSIT', 'UNKNOWN', 'WALK'))
                )
                """);
        jdbcTemplate.execute("""
                create table user_accounts (
                    id varchar(36) primary key,
                    default_travel_mode varchar(20),
                    constraint user_accounts_default_travel_mode_check
                        check (default_travel_mode in ('CAR', 'TRANSIT', 'UNKNOWN', 'WALK'))
                )
                """);
    }

    @Test
    void dropsOldTravelModeConstraintsThatDoNotAllowBicycle() throws Exception {
        assertThatThrownBy(() -> insertParticipant("BICYCLE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUserDefaultTravelMode("BICYCLE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        repair.run(null);

        insertParticipant("BICYCLE");
        insertUserDefaultTravelMode("BICYCLE");

        assertThat(jdbcTemplate.queryForObject("select travel_mode from participants", String.class))
                .isEqualTo("BICYCLE");
        assertThat(jdbcTemplate.queryForObject("select default_travel_mode from user_accounts", String.class))
                .isEqualTo("BICYCLE");
    }

    private void insertParticipant(String travelMode) {
        jdbcTemplate.update("insert into participants (id, travel_mode) values (?, ?)",
                UUID.randomUUID().toString(), travelMode);
    }

    private void insertUserDefaultTravelMode(String travelMode) {
        jdbcTemplate.update("insert into user_accounts (id, default_travel_mode) values (?, ?)",
                UUID.randomUUID().toString(), travelMode);
    }
}
