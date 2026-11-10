package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Converts legacy hotel-local DATETIME storage to UTC after validating the source zone. */
public final class V64__utc_datetime_storage extends BaseJavaMigration {
    private record Trigger(String name, String table, String timing, String event,
                           String body, String sqlMode) {}
    private record Row(List<Object> key, List<LocalDateTime> values) {}
    private record Table(String name, List<String> keyColumns,
                         List<String> columns, List<Row> rows) {}

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        requireUtcSession(connection);
        createRecoveryTables(connection);
        if (alreadyConverted(connection)) {
            restoreTriggers(connection);
            return;
        }

        boolean freshSchema = isFreshSchema(connection);
        String legacyZoneId = System.getProperty("hotel.legacy.datetime.zone-id",
                System.getenv("HOTEL_LEGACY_DATETIME_TIME_ZONE"));
        if (!freshSchema && (legacyZoneId == null || legacyZoneId.isBlank())) {
            throw new IllegalStateException("Set HOTEL_LEGACY_DATETIME_TIME_ZONE explicitly "
                    + "before converting an existing database");
        }
        ZoneId legacyZone = ZoneId.of(freshSchema
                ? System.getenv().getOrDefault("HOTEL_TIME_ZONE", "America/New_York")
                : legacyZoneId);
        if (freshSchema) {
            markConverted(connection, legacyZone, false);
            return;
        }

        List<Table> changes = readAndValidateLegacyRows(connection, legacyZone);
        saveUpdateTriggers(connection);
        try {
            dropSavedTriggers(connection);
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                applyChanges(connection, changes);
                markConverted(connection, legacyZone, true);
                connection.commit();
            } catch (Exception failed) {
                connection.rollback();
                throw failed;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } finally {
            restoreTriggers(connection);
        }
    }

    private void requireUtcSession(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT @@session.time_zone")) {
            result.next();
            String zone = result.getString(1);
            if (!"+00:00".equals(zone) && !"UTC".equalsIgnoreCase(zone)) {
                throw new IllegalStateException("UTC JDBC session is required before datetime conversion");
            }
        }
    }

    private void createRecoveryTables(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS hotel_datetime_migration_marker (
                        id TINYINT PRIMARY KEY,
                        legacy_zone VARCHAR(80) NOT NULL,
                        legacy_rows_converted TINYINT NOT NULL,
                        converted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS hotel_datetime_trigger_backup (
                        trigger_name VARCHAR(64) PRIMARY KEY,
                        table_name VARCHAR(64) NOT NULL,
                        timing VARCHAR(8) NOT NULL,
                        event_name VARCHAR(8) NOT NULL,
                        body LONGTEXT NOT NULL,
                        sql_mode TEXT NOT NULL
                    ) ENGINE=InnoDB
                    """);
        }
    }

    private boolean alreadyConverted(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM hotel_datetime_migration_marker WHERE id=1")) {
            result.next();
            return result.getInt(1) == 1;
        }
    }

    private boolean isFreshSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TIMESTAMPDIFF(SECOND,
                         (SELECT installed_on FROM flyway_schema_history WHERE version='1'),
                         (SELECT installed_on FROM flyway_schema_history WHERE version='63')),
                         EXISTS (SELECT 1 FROM sys_user WHERE username<>'system_night_audit'),
                         EXISTS (SELECT 1 FROM room),
                         EXISTS (SELECT 1 FROM booking),
                         EXISTS (SELECT 1 FROM hotel_business_date_history)
                     """)) {
            result.next();
            long separation = result.getLong(1);
            boolean hasInstallationTimes = !result.wasNull();
            return hasInstallationTimes && separation >= 0 && separation < 600
                    && !result.getBoolean(2) && !result.getBoolean(3)
                    && !result.getBoolean(4) && !result.getBoolean(5);
        }
    }

    private void markConverted(Connection connection, ZoneId zone, boolean converted)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO hotel_datetime_migration_marker
                    (id,legacy_zone,legacy_rows_converted) VALUES (1,?,?)
                """)) {
            statement.setString(1, zone.getId());
            statement.setBoolean(2, converted);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Datetime conversion marker was not created");
            }
        }
    }

    private List<Table> readAndValidateLegacyRows(Connection connection, ZoneId zone)
            throws SQLException {
        List<String> tables = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT DISTINCT TABLE_NAME FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE='datetime'
                       AND TABLE_NAME NOT IN (
                           'hotel_datetime_migration_marker','hotel_datetime_trigger_backup')
                     ORDER BY TABLE_NAME
                     """)) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }

        List<Table> changes = new ArrayList<>();
        for (String table : tables) {
            List<String> keys = primaryKeyColumns(connection, table);
            List<String> columns = datetimeColumns(connection, table);
            String projection = columns.stream().map(column -> "`" + column + "`")
                    .collect(Collectors.joining(","));
            String keyProjection = keys.stream().map(column -> "`" + column + "`")
                    .collect(Collectors.joining(","));
            String sql = "SELECT " + keyProjection + "," + projection + " FROM `" + table + "`";
            if ("sys_user".equals(table)) {
                sql += " WHERE username<>'system_night_audit'";
            }
            List<Row> rows = new ArrayList<>();
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(sql)) {
                while (result.next()) {
                    List<Object> key = new ArrayList<>();
                    for (int index = 0; index < keys.size(); index++) {
                        key.add(result.getObject(index + 1));
                    }
                    List<LocalDateTime> converted = new ArrayList<>();
                    boolean hasValue = false;
                    for (int index = 0; index < columns.size(); index++) {
                        LocalDateTime old = result.getObject(keys.size() + index + 1, LocalDateTime.class);
                        hasValue |= old != null;
                        converted.add(old == null ? null
                                : convert(old, zone, table, columns.get(index), key));
                    }
                    if (hasValue) {
                        rows.add(new Row(key, converted));
                    }
                }
            } catch (SQLException invalidTable) {
                throw new SQLException("Cannot read legacy datetime table " + table, invalidTable);
            }
            changes.add(new Table(table, keys, columns, rows));
        }
        return changes;
    }

    private List<String> datetimeColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND DATA_TYPE='datetime'
                ORDER BY ORDINAL_POSITION
                """)) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
        }
        return columns;
    }

    private List<String> primaryKeyColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY'
                ORDER BY ORDINAL_POSITION
                """)) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("Legacy datetime table has no primary key: " + table);
        }
        return columns;
    }

    private LocalDateTime convert(LocalDateTime old, ZoneId zone,
                                  String table, String column, List<Object> key) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(old);
        if (offsets.size() != 1) {
            throw new IllegalStateException("Legacy datetime needs manual DST resolution: "
                    + table + "." + column + " key=" + key + " value=" + old);
        }
        return LocalDateTime.ofInstant(old.toInstant(offsets.get(0)), ZoneOffset.UTC);
    }

    private void applyChanges(Connection connection, List<Table> tables) throws SQLException {
        for (Table table : tables) {
            if (table.rows().isEmpty()) {
                continue;
            }
            String assignments = table.columns().stream()
                    .map(column -> "`" + column + "`=?")
                    .collect(Collectors.joining(","));
            String condition = table.keyColumns().stream()
                    .map(column -> "`" + column + "`=?")
                    .collect(Collectors.joining(" AND "));
            try (var statement = connection.prepareStatement(
                    "UPDATE `" + table.name() + "` SET " + assignments + " WHERE " + condition)) {
                for (Row row : table.rows()) {
                    for (int index = 0; index < row.values().size(); index++) {
                        statement.setObject(index + 1, row.values().get(index));
                    }
                    for (int index = 0; index < row.key().size(); index++) {
                        statement.setObject(row.values().size() + index + 1, row.key().get(index));
                    }
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Datetime row changed during migration: "
                                + table.name() + " key=" + row.key());
                    }
                }
            }
        }
    }

    private void saveUpdateTriggers(Connection connection) throws SQLException {
        List<Trigger> triggers = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TRIGGER_NAME,EVENT_OBJECT_TABLE,ACTION_TIMING,
                            EVENT_MANIPULATION,ACTION_STATEMENT,SQL_MODE
                     FROM information_schema.TRIGGERS
                     WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_MANIPULATION='UPDATE'
                     ORDER BY EVENT_OBJECT_TABLE,TRIGGER_NAME
                     """)) {
            while (result.next()) {
                triggers.add(new Trigger(result.getString(1), result.getString(2),
                        result.getString(3), result.getString(4),
                        result.getString(5), result.getString(6)));
            }
        }
        try (var statement = connection.prepareStatement("""
                INSERT IGNORE INTO hotel_datetime_trigger_backup
                    (trigger_name,table_name,timing,event_name,body,sql_mode)
                VALUES (?,?,?,?,?,?)
                """)) {
            for (Trigger trigger : triggers) {
                statement.setString(1, trigger.name());
                statement.setString(2, trigger.table());
                statement.setString(3, trigger.timing());
                statement.setString(4, trigger.event());
                statement.setString(5, trigger.body());
                statement.setString(6, trigger.sqlMode());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<Trigger> savedTriggers(Connection connection) throws SQLException {
        List<Trigger> triggers = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT trigger_name,table_name,timing,event_name,body,sql_mode
                     FROM hotel_datetime_trigger_backup
                     ORDER BY table_name,trigger_name
                     """)) {
            while (result.next()) {
                triggers.add(new Trigger(result.getString(1), result.getString(2),
                        result.getString(3), result.getString(4),
                        result.getString(5), result.getString(6)));
            }
        }
        return triggers;
    }

    private void dropSavedTriggers(Connection connection) throws SQLException {
        for (Trigger trigger : savedTriggers(connection)) {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS `" + trigger.name() + "`");
            }
        }
    }

    private void restoreTriggers(Connection connection) throws SQLException {
        String originalMode;
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT @@session.sql_mode")) {
            result.next();
            originalMode = result.getString(1);
        }
        try {
            for (Trigger trigger : savedTriggers(connection)) {
                if (triggerExists(connection, trigger.name())) {
                    continue;
                }
                setSqlMode(connection, trigger.sqlMode());
                String sql = "CREATE TRIGGER `" + trigger.name() + "` "
                        + trigger.timing() + " " + trigger.event() + " ON `"
                        + trigger.table() + "` FOR EACH ROW " + trigger.body();
                try (var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        } finally {
            setSqlMode(connection, originalMode);
        }
    }

    private boolean triggerExists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.TRIGGERS
                WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?
                """)) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private void setSqlMode(Connection connection, String mode) throws SQLException {
        try (var statement = connection.prepareStatement("SET SESSION sql_mode=?")) {
            statement.setString(1, mode);
            statement.execute();
        }
    }
}
