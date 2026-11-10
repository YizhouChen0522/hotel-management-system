package com.johnny.hotel.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Restores only a disposable wallet schema to its post-migration baseline. */
public final class IsolatedWalletDatabaseReset {
    private record Table(String name, List<String> columns, List<Object[]> rows) {}

    private List<Table> baseline;

    public synchronized boolean isCaptured() {
        return baseline != null;
    }

    public synchronized void captureOrRestore(JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            assertDisposableSchema(connection);
            if (baseline == null) {
                baseline = capture(connection);
            } else {
                restore(connection);
            }
            return null;
        });
    }

    private void assertDisposableSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE(), @@port")) {
            if (!result.next()
                    || !result.getString(1).matches("hotel_wallet_isolated_[0-9a-f]{12}")
                    || result.getInt(2) != 3306) {
                throw new IllegalStateException("Refusing to reset a non-isolated wallet database");
            }
        }
    }

    private List<Table> capture(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TABLE_NAME FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'
                       AND TABLE_NAME<>'flyway_schema_history'
                     ORDER BY TABLE_NAME
                     """)) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }

        List<Table> tables = new ArrayList<>();
        for (String name : names) {
            tables.add(captureTable(connection, name));
        }
        return tables;
    }

    private Table captureTable(Connection connection, String name) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?
                  AND EXTRA NOT LIKE '%GENERATED%'
                ORDER BY ORDINAL_POSITION
                """)) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
        }
        String projection = columns.stream()
                .map(column -> "`" + column + "`")
                .collect(Collectors.joining(","));
        List<Object[]> rows = new ArrayList<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT " + projection + " FROM `" + name + "`")) {
            while (result.next()) {
                Object[] row = new Object[columns.size()];
                for (int index = 0; index < columns.size(); index++) {
                    row[index] = result.getObject(index + 1);
                }
                rows.add(row);
            }
        }
        return new Table(name, columns, rows);
    }

    private void restore(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                for (Table table : baseline) {
                    if (hasRows(connection, table.name())) {
                        statement.execute("TRUNCATE TABLE `" + table.name() + "`");
                    }
                }
                for (Table table : baseline) {
                    insertRows(connection, table);
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    private boolean hasRows(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
            return result.next();
        }
    }

    private void insertRows(Connection connection, Table table) throws SQLException {
        if (table.rows().isEmpty()) {
            return;
        }
        String columns = table.columns().stream()
                .map(column -> "`" + column + "`")
                .collect(Collectors.joining(","));
        String placeholders = String.join(",", java.util.Collections.nCopies(table.columns().size(), "?"));
        String sql = "INSERT INTO `" + table.name() + "` (" + columns + ") VALUES (" + placeholders + ")";
        try (var statement = connection.prepareStatement(sql)) {
            for (Object[] row : table.rows()) {
                for (int column = 0; column < row.length; column++) {
                    statement.setObject(column + 1, row[column]);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
