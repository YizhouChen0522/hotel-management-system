package com.johnny.hotel.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Maps the hotel-local Java view of an absolute timestamp to a UTC MySQL DATETIME. */
public final class HotelDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {
    private final ZoneId hotelZone;
    private final Clock clock;

    public HotelDateTimeTypeHandler(Clock clock) {
        this.hotelZone = clock.getZone();
        this.clock = clock;
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    LocalDateTime value, JdbcType jdbcType) throws SQLException {
        statement.setObject(index, toUtc(value));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet result, String column) throws SQLException {
        return fromUtc(result.getObject(column, LocalDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet result, int column) throws SQLException {
        return fromUtc(result.getObject(column, LocalDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement statement, int column) throws SQLException {
        return fromUtc(statement.getObject(column, LocalDateTime.class));
    }

    public LocalDateTime toUtc(LocalDateTime hotelLocal) {
        List<ZoneOffset> offsets = hotelZone.getRules().getValidOffsets(hotelLocal);
        if (offsets.size() == 1) {
            return LocalDateTime.ofInstant(hotelLocal.toInstant(offsets.get(0)), ZoneOffset.UTC);
        }
        if (offsets.isEmpty()) {
            throw new IllegalArgumentException("Hotel-local time falls in a daylight-saving gap: " + hotelLocal);
        }
        Instant now = clock.instant();
        for (ZoneOffset offset : offsets) {
            Instant candidate = hotelLocal.toInstant(offset);
            if (Duration.between(now, candidate).abs().compareTo(Duration.ofMinutes(2)) <= 0) {
                return LocalDateTime.ofInstant(candidate, ZoneOffset.UTC);
            }
        }
        throw new IllegalArgumentException("Ambiguous hotel-local time needs an explicit offset: " + hotelLocal);
    }

    public LocalDateTime fromUtc(LocalDateTime utc) {
        return utc == null ? null : LocalDateTime.ofInstant(utc.toInstant(ZoneOffset.UTC), hotelZone);
    }
}
