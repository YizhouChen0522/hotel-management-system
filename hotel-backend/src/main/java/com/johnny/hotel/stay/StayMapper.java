package com.johnny.hotel.stay;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StayMapper {
    String FILTER="<where><if test='owner!=null'>b.user_id=#{owner}</if><if test='booking!=null'> AND s.booking_id=#{booking}</if><if test='status!=null'> AND s.status=#{status}</if></where>";
    @Select("<script>SELECT s.* FROM stay s JOIN booking b ON b.id=s.booking_id "+FILTER+" ORDER BY s.actual_check_in_time DESC,s.id DESC LIMIT #{offset},#{size}</script>")
    List<Stay> page(@Param("owner") Long owner,@Param("booking")Long booking,@Param("status")Integer status,@Param("offset")int offset,@Param("size")int size);
    @Select("<script>SELECT COUNT(*) FROM stay s JOIN booking b ON b.id=s.booking_id "+FILTER+"</script>")
    long count(@Param("owner")Long owner,@Param("booking")Long booking,@Param("status")Integer status);
    @Select("SELECT * FROM stay WHERE id=#{id}") Stay find(Long id);
    @Select("SELECT * FROM stay WHERE booking_id=#{bookingId}") Stay byBooking(Long bookingId);
    @Select("SELECT * FROM stay WHERE id=#{id} FOR UPDATE") Stay lock(Long id);
    @Select("SELECT * FROM stay WHERE booking_id=#{bookingId} FOR UPDATE") Stay lockByBooking(Long bookingId);
    @Insert("""
        INSERT INTO stay(booking_id,primary_guest_id,registration_id,status,actual_check_in_time,checked_in_by)
        VALUES(#{bookingId},#{primaryGuestId},#{registrationId},1,#{actualCheckInTime},#{checkedInBy})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(Stay stay);
    @Update("""
        UPDATE stay SET status=2,actual_check_out_time=#{time},checked_out_by=#{actor}
        WHERE id=#{id} AND status=1 AND actual_check_out_time IS NULL AND actual_check_in_time<=#{time}
        """) int close(@Param("id") Long id,@Param("time") LocalDateTime time,@Param("actor") Long actor);
    @Insert("""
        INSERT INTO stay_guest(stay_id,guest_id,guest_role,registered_by)
        VALUES(#{stayId},#{guestId},#{guestRole},#{registeredBy})
        """) @Options(useGeneratedKeys=true,keyProperty="id") int addGuest(StayGuest guest);
    @Select("SELECT * FROM stay_guest WHERE stay_id=#{stayId} ORDER BY guest_role,id") List<StayGuest> guests(Long stayId);
    @Select("SELECT * FROM stay_guest WHERE stay_id=#{stayId} ORDER BY guest_role,id FOR UPDATE") List<StayGuest> guestsForUpdate(Long stayId);
}
