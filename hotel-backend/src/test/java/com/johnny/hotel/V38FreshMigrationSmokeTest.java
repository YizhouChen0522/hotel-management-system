package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "hotel.v38.fresh.smoke", matches = "true")
class V38FreshMigrationSmokeTest {
    @Test void zeroUserFreshV1ThroughLatest() throws Exception {
        var settings = WalletDevelopmentGuard.settings();
        String database = "hotel_v38_smoke_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String original = WalletDevelopmentGuard.url();
        String temporary = original.replace("/hotel_management?", "/" + database + "?");
        try (var admin = DriverManager.getConnection(original, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                var flyway = Flyway.configure().dataSource(temporary, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"))
                        .locations("classpath:db/migration").load();
                flyway.migrate();
                flyway.validate();
                try (var temporaryConnection = DriverManager.getConnection(temporary, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
                     var check = temporaryConnection.createStatement();
                     var result = check.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='45'")) {
                    result.next();
                    assertEquals(1, result.getInt(1));
                }
                try (var temporaryConnection = DriverManager.getConnection(temporary, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
                     var check = temporaryConnection.createStatement();
                     var result = check.executeQuery("SELECT business_date,(SELECT COUNT(*) FROM hotel_business_date_history) FROM hotel_business_date_control WHERE id=1")) {
                    result.next();
                    assertEquals(null, result.getDate(1));
                    assertEquals(0, result.getInt(2));
                }
                try (var temporaryConnection = DriverManager.getConnection(temporary, settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
                     var check = temporaryConnection.createStatement();
                     var result = check.executeQuery("SELECT (SELECT COUNT(*) FROM sys_user),(SELECT COUNT(*) FROM dynamic_pricing_policy),(SELECT COUNT(*) FROM dynamic_occupancy_band),(SELECT COUNT(*) FROM dynamic_booking_window_band),(SELECT COUNT(*) FROM dynamic_pricing_cell)")) {
                    result.next();
                    for(int i=1;i<=5;i++) assertEquals(0,result.getInt(i));
                }
            } finally {
                statement.execute("DROP DATABASE `" + database + "`");
            }
        }
    }
}
