package com.johnny.hotel;

import com.johnny.hotel.config.HotelClockConfig;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.sql.DriverManager;
import java.sql.SQLException;
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

    @Test void baselineIsReadOnlyAndForcedSessionsAreEitherAlignedOrExplicitlyUnsupported() throws Exception {
        Snapshot before=snapshot(WalletDevelopmentGuard.url());
        System.out.printf("JDBC_TIMEZONE_BEFORE session=%s global=%s system=%s now=%s utc=%s offsetSeconds=%d%n",
                before.session(),before.global(),before.system(),before.now(),before.utcNow(),before.offsetSeconds());

        TimeZone original=TimeZone.getDefault();
        try {
            for(String id:new String[]{"Asia/Shanghai","America/Toronto"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(id));
                ZoneId system=ZoneId.systemDefault();
                Clock clock=new HotelClockConfig().hotelClock();
                assertEquals(system,clock.getZone());
                Instant instant=Instant.now(clock);
                int expected=system.getRules().getOffset(instant).getTotalSeconds();
                try {
                    Snapshot after=snapshot(WalletDevelopmentGuard.url()+"&forceConnectionTimeZoneToSession=true");
                    System.out.printf("JDBC_TIMEZONE_AFTER zone=%s session=%s global=%s system=%s now=%s utc=%s dbOffsetSeconds=%d jvmOffsetSeconds=%d%n",
                            system,after.session(),after.global(),after.system(),after.now(),after.utcNow(),after.offsetSeconds(),expected);
                    assertEquals(expected,after.offsetSeconds(),"MySQL session offset must follow the current JVM zone");
                } catch (SQLException unsupported) {
                    System.out.printf("JDBC_TIMEZONE_FORCE_UNSUPPORTED zone=%s errorCode=%d%n",system,unsupported.getErrorCode());
                    assertEquals(1298,unsupported.getErrorCode(),"Only missing MySQL named-timezone support is an accepted incompatibility");
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
