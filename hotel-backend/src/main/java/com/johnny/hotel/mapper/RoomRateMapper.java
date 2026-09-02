package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.RoomRate;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RoomRateMapper {

    @Select("""
            SELECT *
            FROM room_rate
            WHERE room_type_id = #{roomTypeId}
              AND rate_date = #{rateDate}
            """)
    RoomRate selectByRoomTypeIdAndDate(
            @Param("roomTypeId") Long roomTypeId,
            @Param("rateDate") LocalDate rateDate
    );

    @Select("""
            SELECT *
            FROM room_rate
            WHERE room_type_id = #{roomTypeId}
              AND rate_date >= #{startDate}
              AND rate_date < #{endDate}
            ORDER BY rate_date ASC
            """)
    List<RoomRate> selectByRoomTypeIdAndDateRange(
            @Param("roomTypeId") Long roomTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Insert("""
            INSERT INTO room_rate
            (
                room_type_id,
                rate_date,
                price,
                rate_source,
                description
            )
            VALUES
            (
                #{roomTypeId},
                #{rateDate},
                #{price},
                #{rateSource},
                #{description}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RoomRate roomRate);

    @Update("""
            UPDATE room_rate
            SET price = #{price},
                rate_source = #{rateSource},
                description = #{description},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int update(RoomRate roomRate);

    @Delete("""
            DELETE FROM room_rate
            WHERE id = #{id}
            """)
    int deleteById(@Param("id") Long id);
}