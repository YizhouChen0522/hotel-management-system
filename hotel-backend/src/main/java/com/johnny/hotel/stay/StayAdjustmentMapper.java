package com.johnny.hotel.stay;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface StayAdjustmentMapper {
    @Select("SELECT * FROM stay_adjustment WHERE booking_id=#{id} ORDER BY id FOR UPDATE")
    List<StayAdjustment> forBooking(Long id);
    @Select("SELECT * FROM stay_extension_nightly_rate WHERE booking_id=#{id} ORDER BY stay_date FOR UPDATE")
    List<ExtensionNightlyRate> nights(Long id);
    @Insert("""
        INSERT INTO stay_adjustment(booking_id,folio_id,assignment_id,adjustment_type,old_end,new_end,effective_time,request_key,reason,operator_id,previous_late_id,conflict_booking_id,locked_rate,rate_source,basis_item_id)
        VALUES(#{bookingId},#{folioId},#{assignmentId},#{adjustmentType},#{oldEnd},#{newEnd},#{effectiveTime},#{requestKey},#{reason},#{operatorId},#{previousLateId},#{conflictBookingId},#{lockedRate},#{rateSource},#{basisItemId})
        """) @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(StayAdjustment adjustment);
    @Insert("""
        INSERT INTO stay_extension_nightly_rate(adjustment_id,booking_id,stay_date,room_type_id,rate_amount,rate_source,currency)
        VALUES(#{adjustmentId},#{bookingId},#{stayDate},#{roomTypeId},#{rateAmount},#{rateSource},#{currency})
        """) @Options(useGeneratedKeys=true,keyProperty="id")
    int insertNight(ExtensionNightlyRate rate);
}
