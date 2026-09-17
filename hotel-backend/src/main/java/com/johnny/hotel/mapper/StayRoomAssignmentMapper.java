package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.StayRoomAssignment;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StayRoomAssignmentMapper {
    @Select("SELECT a.*,(SELECT s.booking_id FROM stay s WHERE s.id=a.stay_id) AS booking_id FROM stay_room_assignment a WHERE stay_id=#{stayId} ORDER BY start_time,id")
    List<StayRoomAssignment> readByStayId(Long stayId);
    @Select("SELECT a.* FROM stay_room_assignment a WHERE stay_id=#{stayId} AND end_time IS NULL")
    StayRoomAssignment activeIdentity(Long stayId);

    @Insert("""
            INSERT INTO stay_room_assignment
            (
                stay_id,
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
                #{stayId},
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
            StayRoomAssignment assignment
    );


    @Select("""
            SELECT a.*,(SELECT s.booking_id FROM stay s WHERE s.id=a.stay_id) AS booking_id
            FROM stay_room_assignment a
            WHERE a.id = #{id}
            FOR UPDATE
            """)
    StayRoomAssignment selectById(
            @Param("id") Long id
    );


    @Select("""
            SELECT a.*,(SELECT s.booking_id FROM stay s WHERE s.id=a.stay_id) AS booking_id
            FROM stay_room_assignment a
            WHERE a.stay_id = (SELECT id FROM stay WHERE booking_id=#{bookingId})
            ORDER BY a.start_time ASC, a.id ASC
            FOR UPDATE
            """)
    List<StayRoomAssignment> selectByBookingId(
            @Param("bookingId") Long bookingId
    );


    @Select("""
            SELECT a.*,(SELECT s.booking_id FROM stay s WHERE s.id=a.stay_id) AS booking_id
            FROM stay_room_assignment a
            WHERE a.stay_id = (SELECT id FROM stay WHERE booking_id=#{bookingId})
              AND end_time IS NULL
            ORDER BY a.id DESC
            FOR UPDATE
            """)
    StayRoomAssignment selectActiveByBookingId(
            @Param("bookingId") Long bookingId
    );


    @Update("""
            UPDATE stay_room_assignment
            SET end_time = #{endTime}
            WHERE id = #{id}
              AND end_time IS NULL AND start_time <= #{endTime}
            """)
    int closeAssignment(
            @Param("id") Long id,
            @Param("endTime") LocalDateTime endTime
    );
    @Select("SELECT a.*,(SELECT s.booking_id FROM stay s WHERE s.id=a.stay_id) AS booking_id FROM stay_room_assignment a WHERE a.stay_id=#{stayId} ORDER BY a.start_time,a.id FOR UPDATE")
    List<StayRoomAssignment> selectByStayId(Long stayId);
    @Select("SELECT a.*,(SELECT s.booking_id FROM stay s WHERE s.id=a.stay_id) AS booking_id FROM stay_room_assignment a WHERE a.stay_id=#{stayId} AND a.end_time IS NULL FOR UPDATE")
    StayRoomAssignment selectActiveByStayId(Long stayId);
}
