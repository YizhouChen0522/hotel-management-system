package com.johnny.hotel.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** A disposable schema on the local development MySQL server, never the shared PMS schema. */
public final class IsolatedWalletDatabase {
    private static final String NAME = "hotel_wallet_isolated_"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static String url;

    private IsolatedWalletDatabase() {}

    public static synchronized String url() {
        if (url != null) {
            return url;
        }
        var settings = WalletDevelopmentGuard.settings();
        String base = WalletDevelopmentGuard.url();
        String isolated = base.replace("/hotel_management?", "/" + NAME + "?");
        try (var connection = DriverManager.getConnection(
                base, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + NAME
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("Isolated wallet test schema could not be created", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var connection = DriverManager.getConnection(
                    base, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + NAME + "`");
            } catch (SQLException e) {
                System.err.println("Isolated wallet test schema requires manual cleanup: " + NAME);
            }
        }, "wallet-test-schema-cleanup"));
        url = isolated;
        return url;
    }
}
