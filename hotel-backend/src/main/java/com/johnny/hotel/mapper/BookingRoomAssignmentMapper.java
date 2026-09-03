package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.BookingRoomAssignment;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BookingRoomAssignmentMapper {

    @Insert("""
            INSERT INTO booking_room_assignment
            (
                booking_id,
                room_id,
                room_type_id,
                assignment_type,
                start_time,
                end_time,
                change_reason,
                created_by
            )
            VALUES
            (
                #{bookingId},
                #{roomId},
                #{roomTypeId},
                #{assignmentType},
                #{startTime},
                #{endTime},
                #{changeReason},
                #{createdBy}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    int insert(
            BookingRoomAssignment assignment
    );


    @Select("""
            SELECT *
            FROM booking_room_assignment
            WHERE id = #{id}
            """)
    BookingRoomAssignment selectById(
            @Param("id") Long id
    );


    @Select("""
            SELECT *
            FROM booking_room_assignment
            WHERE booking_id = #{bookingId}
            ORDER BY start_time ASC, id ASC
            """)
    List<BookingRoomAssignment> selectByBookingId(
            @Param("bookingId") Long bookingId
    );


    @Select("""
            SELECT *
            FROM booking_room_assignment
            WHERE booking_id = #{bookingId}
              AND end_time IS NULL
            ORDER BY id DESC
            LIMIT 1
            """)
    BookingRoomAssignment selectActiveByBookingId(
            @Param("bookingId") Long bookingId
    );


    @Update("""
            UPDATE booking_room_assignment
            SET end_time = #{endTime}
            WHERE id = #{id}
              AND end_time IS NULL
            """)
    int closeAssignment(
            @Param("id") Long id,
            @Param("endTime") LocalDateTime endTime
    );
}
