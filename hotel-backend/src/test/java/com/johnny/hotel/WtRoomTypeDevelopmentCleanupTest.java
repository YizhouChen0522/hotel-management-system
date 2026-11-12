package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit, guarded cleanup of room types made by wt_* development tests. */
@EnabledIfSystemProperty(named = "hotel.wt.room.cleanup", matches = "true")
class WtRoomTypeDevelopmentCleanupTest {
    private static final String NAMESPACE = "wt_[0-9a-f]{16}_(standard|deluxe)";

    @Test
    void removeUnusedTypesAndHideHistoricallyReferencedTypes() throws Exception {
        var settings = WalletDevelopmentGuard.settings();
        try (var connection = DriverManager.getConnection(
                WalletDevelopmentGuard.url(), settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                verifyDevelopmentDatabase(connection);
                var references = referenceColumns(connection);
                int removed = 0;
                int retained = 0;
                try (var select = connection.prepareStatement(
                        "SELECT id, type_name FROM room_type WHERE type_name REGEXP ? ORDER BY id FOR UPDATE")) {
                    select.setString(1, NAMESPACE);
                    try (var types = select.executeQuery()) {
                        while (types.next()) {
                            long typeId = types.getLong(1);
                            String typeName = types.getString(2);
                            assertTrue(typeName.matches(NAMESPACE));
                            var roomIds = roomIds(connection, typeId);
                            if (hasBusinessReferences(connection, references, typeId, roomIds)) {
                                update(connection, "UPDATE room_type SET status = 0 WHERE id = ?", typeId);
                                retained++;
                            } else {
                                for (long roomId : roomIds) {
                                    assertEquals(1, roomStatus(connection, roomId),
                                            "Unused fixture room is not AVAILABLE: " + roomId);
                                }
                                update(connection, "DELETE FROM room_rate WHERE room_type_id = ?", typeId);
                                update(connection, "DELETE FROM room WHERE room_type_id = ?", typeId);
                                assertEquals(1, update(connection, "DELETE FROM room_type WHERE id = ?", typeId));
                                removed++;
                            }
                        }
                    }
                }
                assertEquals(19, removed + retained, "Fixture inventory changed; review before cleanup");
                assertEquals(0, count(connection,
                        "SELECT COUNT(*) FROM room_type WHERE type_name REGEXP ? AND status = 1", NAMESPACE));
                connection.commit();
                System.out.printf("wt_* room types: removed %d unused, retained %d historical (disabled).%n",
                        removed, retained);
            } catch (Throwable failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private void verifyDevelopmentDatabase(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE(), @@port")) {
            assertTrue(result.next());
            assertEquals("hotel_management", result.getString(1));
            assertEquals(3306, result.getInt(2));
        }
    }

    private List<ReferenceColumn> referenceColumns(Connection connection) throws SQLException {
        var columns = new ArrayList<ReferenceColumn>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (var tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if (List.of("room", "room_rate", "room_type").contains(table)) {
                    continue;
                }
                try (var fields = metadata.getColumns(connection.getCatalog(), null, table, "%")) {
                    while (fields.next()) {
                        String field = fields.getString("COLUMN_NAME");
                        if (List.of("room_type_id", "room_id", "reserved_room_id").contains(field)) {
                            columns.add(new ReferenceColumn(table, field));
                        }
                    }
                }
            }
        }
        assertTrue(columns.size() >= 10, "Reference inventory unexpectedly small");
        return columns;
    }

    private boolean hasBusinessReferences(Connection connection, List<ReferenceColumn> columns,
                                          long typeId, List<Long> roomIds) throws SQLException {
        for (var reference : columns) {
            if (reference.column().equals("room_type_id")
                    && count(connection, reference.sql(), typeId) > 0) {
                return true;
            }
            if (!reference.column().equals("room_type_id")) {
                for (long roomId : roomIds) {
                    if (count(connection, reference.sql(), roomId) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<Long> roomIds(Connection connection, long typeId) throws SQLException {
        var ids = new ArrayList<Long>();
        try (var statement = connection.prepareStatement("SELECT id FROM room WHERE room_type_id = ? FOR UPDATE")) {
            statement.setLong(1, typeId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getLong(1));
                }
            }
        }
        return ids;
    }

    private int roomStatus(Connection connection, long roomId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT status FROM room WHERE id = ?")) {
            statement.setLong(1, roomId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private long count(Connection connection, String sql, Object parameter) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private int update(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate();
        }
    }

    private record ReferenceColumn(String table, String column) {
        String sql() {
            return "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` = ?";
        }
    }
}
