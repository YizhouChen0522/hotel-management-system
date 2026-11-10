package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.sql.DriverManager;
import java.time.*;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="hotel.wallet.dev.tests", matches="true")
@ResourceLock("JVM_DEFAULT_TIME_ZONE")
class JdbcSessionTimezoneDevelopmentTest {
    private record Snapshot(String session, String global, String system, LocalDateTime now,
                            LocalDateTime utcNow, int offsetSeconds) {}

    private Snapshot snapshot(String url) throws Exception {
        var settings=WalletDevelopmentGuard.settings();
        try(var connection=DriverManager.getConnection(url,settings.get("DB_USERNAME"),settings.get("DB_PASSWORD"));
            var statement=connection.createStatement();
            var result=statement.executeQuery("SELECT @@session.time_zone,@@global.time_zone,@@system_time_zone,NOW(),UTC_TIMESTAMP(),TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),NOW())")) {
            assertTrue(result.next());
            return new Snapshot(result.getString(1),result.getString(2),result.getString(3),
                    result.getTimestamp(4).toLocalDateTime(),result.getTimestamp(5).toLocalDateTime(),result.getInt(6));
        }
    }

    @Test
    void utcSessionAndLocalDateTimeRoundTripIgnoreJvmZone() throws Exception {
        String url = WalletDevelopmentGuard.url();
        TimeZone original = TimeZone.getDefault();
        try {
            for (String id : new String[]{"Asia/Shanghai", "America/Toronto"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(id));
                Snapshot after = snapshot(url);
                assertEquals(0, after.offsetSeconds());
                assertTrue(after.session().equals("+00:00") || after.session().equals("UTC"));
                assertTrue(Math.abs(Duration.between(after.utcNow(), after.now()).toSeconds()) <= 1);

                var settings = WalletDevelopmentGuard.settings();
                try (var connection = DriverManager.getConnection(url,
                        settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
                     var statement = connection.prepareStatement("SELECT CAST(? AS DATETIME(6))")) {
                    LocalDateTime value = LocalDateTime.of(2026, 11, 9, 12, 34, 56);
                    statement.setObject(1, value);
                    try (var result = statement.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(value, result.getObject(1, LocalDateTime.class));
                    }
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
