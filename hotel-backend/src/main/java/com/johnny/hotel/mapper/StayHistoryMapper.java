package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.StayHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface StayHistoryMapper {

    @Insert("""
            INSERT INTO stay_history (
                user_id,
                folio_id,
                assignment_id,
                actual_check_in_time,
                actual_check_out_time,
                room_number,
                room_type_name
            )
            VALUES (
                #{userId},
                #{folioId},
                #{assignmentId},
                #{actualCheckInTime},
                #{actualCheckOutTime},
                #{roomNumber},
                #{roomTypeName}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StayHistory stayHistory);

    @Select("""
            SELECT
                id,
                user_id AS userId,
                folio_id AS folioId,
                assignment_id AS assignmentId,
                actual_check_in_time AS actualCheckInTime,
                actual_check_out_time AS actualCheckOutTime,
                room_number AS roomNumber,
                room_type_name AS roomTypeName,
                create_time AS createTime
            FROM stay_history
            WHERE user_id = #{userId}
            ORDER BY actual_check_in_time DESC
            """)
    List<StayHistory> findByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT
                id,
                user_id AS userId,
                folio_id AS folioId,
                assignment_id AS assignmentId,
                actual_check_in_time AS actualCheckInTime,
                actual_check_out_time AS actualCheckOutTime,
                room_number AS roomNumber,
                room_type_name AS roomTypeName,
                create_time AS createTime
            FROM stay_history
            WHERE folio_id = #{folioId}
              AND user_id = #{userId}
            ORDER BY actual_check_in_time ASC
            """)
    List<StayHistory> findByFolioIdAndUserId(
            @Param("folioId") Long folioId,
            @Param("userId") Long userId
    );

    @Select("""
            SELECT COUNT(*)
            FROM stay_history
            WHERE assignment_id = #{assignmentId}
            """)
    int countByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Select("""
        SELECT
            b.user_id AS userId,
            f.id AS folioId,
            bra.id AS assignmentId,
            bra.start_time AS actualCheckInTime,
            bra.end_time AS actualCheckOutTime,
            r.room_number AS roomNumber,
            rt.type_name AS roomTypeName
        FROM booking_room_assignment bra
        JOIN booking b
            ON b.id = bra.booking_id
        JOIN folio f
            ON f.booking_id = b.id
        JOIN room r
            ON r.id = bra.room_id
        JOIN room_type rt
            ON rt.id = bra.room_type_id
        WHERE bra.booking_id = #{bookingId}
          AND bra.end_time IS NOT NULL
        ORDER BY bra.start_time ASC
        """)
    List<StayHistory> findCompletedAssignmentSnapshots(
            @Param("bookingId") Long bookingId
    );
}