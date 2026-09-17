package com.johnny.hotel.booking.deposit;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DepositMapper {
    @Select("SELECT * FROM deposit_transfer WHERE folio_id=#{folioId} FOR UPDATE") DepositTransfer byFolio(Long folioId);
    @Insert("INSERT INTO reservation_deposit_account(booking_id,currency) VALUES(#{bookingId},#{currency})")
    @Options(useGeneratedKeys=true,keyProperty="id") int open(DepositAccount account);
    @Select("SELECT * FROM reservation_deposit_account WHERE booking_id=#{bookingId}") DepositAccount byBooking(Long bookingId);
    @Select("SELECT * FROM reservation_deposit_account WHERE id=#{id} FOR UPDATE") DepositAccount lock(Long id);
    @Select("SELECT * FROM deposit_payment WHERE account_id=#{id} ORDER BY id FOR UPDATE") List<DepositPayment> payments(Long id);
    @Select("SELECT * FROM deposit_refund WHERE account_id=#{id} ORDER BY id FOR UPDATE") List<DepositRefund> refunds(Long id);
    @Select("SELECT * FROM deposit_transfer WHERE account_id=#{id} ORDER BY id FOR UPDATE") List<DepositTransfer> transfers(Long id);
    @Select("SELECT * FROM deposit_payment WHERE account_id=#{id} ORDER BY received_time DESC,id DESC LIMIT #{offset},#{size}")
    List<DepositPayment> paymentPage(@Param("id") Long id,@Param("offset") int offset,@Param("size") int size);
    @Select("SELECT COUNT(*) FROM deposit_payment WHERE account_id=#{id}") long paymentCount(Long id);
    @Select("SELECT * FROM deposit_refund WHERE account_id=#{id} ORDER BY create_time DESC,id DESC LIMIT #{offset},#{size}")
    List<DepositRefund> refundPage(@Param("id") Long id,@Param("offset") int offset,@Param("size") int size);
    @Select("SELECT COUNT(*) FROM deposit_refund WHERE account_id=#{id}") long refundCount(Long id);
    @Insert("""
        INSERT INTO deposit_payment(account_id,amount,payment_method,reference_no,request_key,received_by,received_time)
        VALUES(#{accountId},#{amount},#{paymentMethod},#{referenceNo},#{requestKey},#{receivedBy},#{receivedTime})
        """) @Options(useGeneratedKeys=true,keyProperty="id") int receive(DepositPayment receipt);
    @Insert("""
        INSERT INTO deposit_refund(account_id,user_id,wallet_id,currency,amount,status,request_key,reason,requested_by)
        VALUES(#{accountId},#{userId},#{walletId},#{currency},#{amount},0,#{requestKey},#{reason},#{requestedBy})
        """) @Options(useGeneratedKeys=true,keyProperty="id") int requestRefund(DepositRefund refund);
    @Update("""
        UPDATE deposit_refund SET status=#{status},processed_by=#{actor},process_key=#{key},process_reason=#{reason},processed_time=NOW(6)
        WHERE id=#{id} AND status=0
        """) int processRefund(@Param("id") Long id,@Param("status") int status,@Param("actor") Long actor,
                                  @Param("key") String key,@Param("reason") String reason);
    @Insert("""
        INSERT INTO deposit_transfer(account_id,stay_id,folio_id,payment_id,amount,event_key,transferred_by)
        VALUES(#{accountId},#{stayId},#{folioId},#{paymentId},#{amount},#{eventKey},#{transferredBy})
        """) @Options(useGeneratedKeys=true,keyProperty="id") int transfer(DepositTransfer transfer);
}
