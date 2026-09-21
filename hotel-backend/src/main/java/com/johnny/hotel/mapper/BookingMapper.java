package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.Booking;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BookingMapper {
    @Insert("""
            INSERT INTO booking (
                user_id,
                booker_guest_profile_id,
                created_by_user_id,
                room_type_id,
                reserved_room_id,
                reservation_source,
                walk_in_request_key,
                staff_direct_request_key,
                portal_request_key,
                reservation_policy_id,
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
                #{bookerGuestProfileId},
                #{createdByUserId},
                #{roomTypeId},
                #{reservedRoomId},
                #{reservationSource},
                #{walkInRequestKey},
                #{staffDirectRequestKey},
                #{portalRequestKey},
                #{reservationPolicyId},
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

    @Select("SELECT * FROM booking WHERE walk_in_request_key=#{key}")
    Booking selectByWalkInRequestKey(String key);

    @Select("SELECT * FROM booking WHERE walk_in_request_key=#{key} FOR UPDATE")
    Booking selectByWalkInRequestKeyForUpdate(String key);

    @Select("SELECT * FROM booking WHERE staff_direct_request_key=#{key} FOR UPDATE")
    Booking selectByStaffDirectRequestKeyForUpdate(String key);

    @Select("SELECT * FROM booking WHERE portal_request_key=#{key} FOR UPDATE")
    Booking selectByPortalRequestKeyForUpdate(String key);

    @Insert("INSERT INTO portal_reservation_request_lock(request_key) VALUES(#{key}) ON DUPLICATE KEY UPDATE request_key=VALUES(request_key)")
    int ensurePortalRequest(String key);

    @Select("SELECT request_key FROM portal_reservation_request_lock WHERE request_key=#{key} FOR UPDATE")
    String lockPortalRequest(String key);

    @Insert("INSERT INTO staff_direct_request_lock(request_key) VALUES(#{key}) ON DUPLICATE KEY UPDATE request_key=VALUES(request_key)")
    int ensureStaffDirectRequest(String key);

    @Select("SELECT request_key FROM staff_direct_request_lock WHERE request_key=#{key} FOR UPDATE")
    String lockStaffDirectRequest(String key);

    @Select("SELECT EXISTS(SELECT 1 FROM booking b WHERE b.reserved_room_id=#{roomId} AND b.id<>#{excluded} AND b.status=1 AND NOT EXISTS(SELECT 1 FROM stay s WHERE s.booking_id=b.id))")
    boolean hasOtherApprovedReservation(@Param("roomId")Long roomId,@Param("excluded")Long excluded);

    @Insert("INSERT INTO walk_in_request_lock(request_key) VALUES(#{key}) ON DUPLICATE KEY UPDATE request_key=VALUES(request_key)")
    int ensureWalkInRequest(String key);

    @Select("SELECT request_key FROM walk_in_request_lock WHERE request_key=#{key} FOR UPDATE")
    String lockWalkInRequest(String key);

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
            SET reserved_room_id = #{assignedRoomId},
                status = #{status},
                update_time = NOW()
            WHERE id = #{id} AND status = 0
            """)
    int approveBooking(@Param("id") Long id,
                       @Param("assignedRoomId") Long assignedRoomId,
                       @Param("status") Integer status);

    @Select("""
        SELECT *
        FROM booking
        WHERE status = #{status}
        ORDER BY create_time DESC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Booking> selectPageByStatus(@Param("status") Integer status,
                                     @Param("offset") Integer offset,
                                     @Param("pageSize") Integer pageSize);

    @Select("""
        SELECT *
        FROM booking
        WHERE user_id = #{userId}
        ORDER BY create_time DESC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Booking> selectPageByUserId(@Param("userId") Long userId,
                                     @Param("offset") Integer offset,
                                     @Param("pageSize") Integer pageSize);

    @Select("""
        SELECT *
        FROM booking
        WHERE check_in_date BETWEEN #{startDate} AND #{endDate}
        ORDER BY check_in_date ASC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Booking> selectPageByCheckInDateRange(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("offset") Integer offset,
                                               @Param("pageSize") Integer pageSize);

    @Select("""
        SELECT *
        FROM booking
        WHERE check_out_date BETWEEN #{startDate} AND #{endDate}
        ORDER BY check_out_date ASC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Booking> selectPageByCheckOutDateRange(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate,
                                                @Param("offset") Integer offset,
                                                @Param("pageSize") Integer pageSize);

    @Update("""
        UPDATE booking
        SET reserved_room_id = #{newRoomId},
            update_time = NOW()
        WHERE id = #{bookingId} AND status = 1
        """)
    int updateAssignedRoom(@Param("bookingId") Long bookingId,
                           @Param("newRoomId") Long newRoomId);

    @Select("""
        SELECT *
        FROM booking
        WHERE id = #{id}
        FOR UPDATE
        """)
    Booking selectByIdForUpdate(@Param("id") Long id);

    @Update("""
        UPDATE booking
        SET room_type_id = #{newRoomTypeId},
            reserved_room_id = #{newRoomId},
            update_time = NOW()
        WHERE id = #{bookingId} AND status = 1
        """)
    int updateRoomTypeAndAssignedRoom(
            @Param("bookingId") Long bookingId,
            @Param("newRoomTypeId") Long newRoomTypeId,
            @Param("newRoomId") Long newRoomId
    );

    @Update("""
        UPDATE booking
        SET total_price = #{totalPrice},
            update_time = NOW()
        WHERE id = #{bookingId} AND status IN (0,1)
        """)
    int updateTotalPrice(
            @Param("bookingId") Long bookingId,
            @Param("totalPrice") BigDecimal totalPrice
    );
    @Update("""
        UPDATE booking
        SET room_type_id = #{roomTypeId},
            guest_count = #{guestCount},
            check_in_date = #{checkInDate},
            check_out_date = #{checkOutDate},
            update_time = NOW()
        WHERE id = #{bookingId} AND status = 0
        """)
    int updatePendingBookingDetails(
            @Param("bookingId") Long bookingId,
            @Param("roomTypeId") Long roomTypeId,
            @Param("guestCount") Integer guestCount,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate
    );

    @Update("UPDATE booking SET status=#{status}, update_time=NOW() WHERE id=#{bookingId} AND status=#{expectedStatus}")
    int transitionStatus(@Param("bookingId") Long bookingId, @Param("expectedStatus") Integer expectedStatus, @Param("status") Integer status);

}
