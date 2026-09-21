package com.johnny.hotel.reservation;

import org.apache.ibatis.annotations.*;

@Mapper public interface ReservationLifecycleMapper {
    @Select("SELECT * FROM reservation_cancellation WHERE booking_id=#{bookingId}") ReservationCancellation cancellation(Long bookingId);
    @Select("SELECT * FROM reservation_no_show WHERE booking_id=#{bookingId}") ReservationNoShow noShow(Long bookingId);
    @Insert("INSERT INTO reservation_cancellation(booking_id,initiator,operator_user_id,reason,policy_id,lead_days,refund_percent,deposit_before,refund_obligation,forfeited_amount) VALUES(#{bookingId},#{initiator},#{operatorUserId},#{reason},#{policyId},#{leadDays},#{refundPercent},#{depositBefore},#{refundObligation},#{forfeitedAmount})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertCancellation(ReservationCancellation fact);
    @Insert("INSERT INTO reservation_no_show(booking_id,operator_user_id,policy_id,reason,deposit_before,forfeited_amount) VALUES(#{bookingId},#{operatorUserId},#{policyId},#{reason},#{depositBefore},#{forfeitedAmount})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertNoShow(ReservationNoShow fact);
}
