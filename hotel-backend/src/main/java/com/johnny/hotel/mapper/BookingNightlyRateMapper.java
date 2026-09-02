package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.BookingNightlyRate;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BookingNightlyRateMapper {

    @Insert("""
            INSERT INTO booking_nightly_rate
            (
                price_version_id,
                booking_id,
                stay_date,
                room_type_id,
                rate_amount,
                rate_source
            )
            VALUES
            (
                #{priceVersionId},
                #{bookingId},
                #{stayDate},
                #{roomTypeId},
                #{rateAmount},
                #{rateSource}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookingNightlyRate nightlyRate);

    @Select("""
            SELECT *
            FROM booking_nightly_rate
            WHERE price_version_id = #{priceVersionId}
            ORDER BY stay_date ASC
            """)
    List<BookingNightlyRate> selectByPriceVersionId(
            @Param("priceVersionId") Long priceVersionId
    );
}
