package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.BookingPriceVersion;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BookingPriceVersionMapper {

    @Insert("""
            INSERT INTO booking_price_version
            (
                booking_id,
                version_no,
                change_type,
                reason,
                is_active,
                total_price,
                currency,
                created_by
            )
            VALUES
            (
                #{bookingId},
                #{versionNo},
                #{changeType},
                #{reason},
                #{isActive},
                #{totalPrice},
                #{currency},
                #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookingPriceVersion version);

    @Select("""
            SELECT *
            FROM booking_price_version
            WHERE booking_id = #{bookingId}
              AND is_active = 1
            LIMIT 1
            """)
    BookingPriceVersion selectActiveByBookingId(
            @Param("bookingId") Long bookingId
    );

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM booking_price_version
            WHERE booking_id = #{bookingId}
            """)
    Integer selectMaxVersionNo(
            @Param("bookingId") Long bookingId
    );

    @Update("""
            UPDATE booking_price_version
            SET is_active = 0
            WHERE booking_id = #{bookingId}
              AND is_active = 1
            """)
    int deactivateActiveVersion(
            @Param("bookingId") Long bookingId
    );
}