package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.Booking;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BookingMapper {
    @Insert("""
            INSERT INTO booking (
                user_id,
                room_type_id,
                assigned_room_id,
                guest_count,
                check_in_date,
                check_out_date,
                status,
                total_price,
                create_time,
                update_time
            )
            VALUES (
                #{userId},
                #{roomTypeId},
                #{assignedRoomId},
                #{guestCount},
                #{checkInDate},
                #{checkOutDate},
                #{status},
                #{totalPrice},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Booking booking);

    @Select("SELECT * FROM booking WHERE id = #{id}")
    Booking selectById(@Param("id") Long id);

    @Select("SELECT * FROM booking WHERE user_id = #{userId} ORDER BY id DESC")
    List<Booking> selectByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT *
        FROM booking
        ORDER BY create_time DESC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Booking> selectPage(@Param("offset") Integer offset,
                             @Param("pageSize") Integer pageSize);

    @Select("""
        SELECT *
        FROM booking
        WHERE status = 0
        ORDER BY create_time ASC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Booking> selectPendingPage(@Param("offset") Integer offset,
                                    @Param("pageSize") Integer pageSize);
    @Update("""
            UPDATE booking
            SET status = #{status},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    @Update("""
            UPDATE booking
            SET assigned_room_id = #{assignedRoomId},
                status = #{status},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int approveBooking(@Param("id") Long id,
                       @Param("assignedRoomId") Long assignedRoomId,
                       @Param("status") Integer status);
}
