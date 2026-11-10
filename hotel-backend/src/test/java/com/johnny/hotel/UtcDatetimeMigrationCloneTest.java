package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import db.migration.V64__utc_datetime_storage;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "hotel.time.clone.db", matches = "hotel_time_clone_[0-9a-f]{12}")
class UtcDatetimeMigrationCloneTest {
    @Test
    void legacyConversionPreservesFactsAndRestoresEveryTrigger() throws Exception {
        String schema = System.getProperty("hotel.time.clone.db");
        assertTrue(schema.matches("hotel_time_clone_[0-9a-f]{12}"));
        var settings = WalletDevelopmentGuard.settings();
        String url = "jdbc:mysql://localhost:3306/" + schema
                + "?useUnicode=true&characterEncoding=utf-8&connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";

        try (Connection connection = DriverManager.getConnection(
                url, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"))) {
            assertEquals(schema, scalar(connection, "SELECT DATABASE()", String.class));
            Set<String> triggers = triggerNames(connection);
            Map<String, String> triggerBodies = triggerDefinitions(connection);
            Map<String, List<LocalDateTime>> legacyValues = datetimeValues(connection);
            LocalDate businessDate = scalar(connection,
                    "SELECT business_date FROM hotel_business_date_control WHERE id=1", LocalDate.class);
            BigDecimal recognized = scalar(connection,
                    "SELECT COALESCE(SUM(amount),0) FROM revenue_recognition", BigDecimal.class);
            LocalDateTime before = scalar(connection,
                    "SELECT create_time FROM booking ORDER BY id LIMIT 1", LocalDateTime.class);
            LocalDateTime systemActorBefore = scalar(connection,
                    "SELECT create_time FROM sys_user WHERE username='system_night_audit'",
                    LocalDateTime.class);

            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V64__utc_datetime_storage().migrate(context);

            assertEquals(1, scalar(connection,
                    "SELECT legacy_rows_converted FROM hotel_datetime_migration_marker WHERE id=1",
                    Integer.class));
            assertEquals(triggers, triggerNames(connection));
            assertEquals(triggerBodies, triggerDefinitions(connection));
            Map<String, List<LocalDateTime>> utcValues = datetimeValues(connection);
            for (var entry : legacyValues.entrySet()) {
                List<LocalDateTime> expected = entry.getValue().stream()
                        .map(value -> value == null ? null : LocalDateTime.ofInstant(
                                value.atZone(ZoneId.of("America/New_York")).toInstant(),
                                ZoneOffset.UTC))
                        .toList();
                assertEquals(expected, utcValues.get(entry.getKey()), entry.getKey());
            }
            assertEquals(businessDate, scalar(connection,
                    "SELECT business_date FROM hotel_business_date_control WHERE id=1", LocalDate.class));
            assertEquals(recognized, scalar(connection,
                    "SELECT COALESCE(SUM(amount),0) FROM revenue_recognition", BigDecimal.class));
            assertEquals(systemActorBefore, scalar(connection,
                    "SELECT create_time FROM sys_user WHERE username='system_night_audit'",
                    LocalDateTime.class));

            LocalDateTime converted = scalar(connection,
                    "SELECT create_time FROM booking ORDER BY id LIMIT 1", LocalDateTime.class);
            assertEquals(LocalDateTime.ofInstant(
                    before.atZone(ZoneId.of("America/New_York")).toInstant(), ZoneOffset.UTC),
                    converted);
            new V64__utc_datetime_storage().migrate(context);
            assertEquals(converted, scalar(connection,
                    "SELECT create_time FROM booking ORDER BY id LIMIT 1", LocalDateTime.class));
            assertEquals(triggers, triggerNames(connection));
        }
    }

    private <T> T scalar(Connection connection, String sql, Class<T> type) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getObject(1, type);
        }
    }

    private Set<String> triggerNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TRIGGER_NAME FROM information_schema.TRIGGERS
                     WHERE TRIGGER_SCHEMA=DATABASE()
                     """)) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private Map<String, String> triggerDefinitions(Connection connection) throws SQLException {
        Map<String, String> definitions = new LinkedHashMap<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TRIGGER_NAME,EVENT_OBJECT_TABLE,ACTION_TIMING,
                            EVENT_MANIPULATION,ACTION_STATEMENT,SQL_MODE
                     FROM information_schema.TRIGGERS
                     WHERE TRIGGER_SCHEMA=DATABASE()
                     ORDER BY TRIGGER_NAME
                     """)) {
            while (result.next()) {
                definitions.put(result.getString(1), result.getString(2) + "|"
                        + result.getString(3) + "|" + result.getString(4) + "|"
                        + result.getString(5) + "|" + result.getString(6));
            }
        }
        return definitions;
    }

    private Map<String, List<LocalDateTime>> datetimeValues(Connection connection)
            throws SQLException {
        Map<String, List<LocalDateTime>> values = new LinkedHashMap<>();
        List<String[]> columns = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE='datetime'
                       AND TABLE_NAME NOT IN
                           ('hotel_datetime_migration_marker','hotel_datetime_trigger_backup')
                     ORDER BY TABLE_NAME,ORDINAL_POSITION
                     """)) {
            while (result.next()) {
                columns.add(new String[]{result.getString(1), result.getString(2)});
            }
        }
        for (String[] column : columns) {
            String sql = "SELECT `" + column[1] + "` FROM `" + column[0] + "`";
            if ("sys_user".equals(column[0])) {
                sql += " WHERE username<>'system_night_audit'";
            }
            sql += " ORDER BY 1";
            List<LocalDateTime> rows = new ArrayList<>();
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(sql)) {
                while (result.next()) {
                    rows.add(result.getObject(1, LocalDateTime.class));
                }
            }
            values.put(column[0] + "." + column[1], rows);
        }
        return values;
    }
}
