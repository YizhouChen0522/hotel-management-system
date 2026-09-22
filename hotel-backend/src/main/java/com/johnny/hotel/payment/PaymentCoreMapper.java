package com.johnny.hotel.payment;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentCoreMapper {
    @Insert("INSERT INTO reservation_checkout_request_lock(customer_user_id,request_key) VALUES(#{user},#{key}) ON DUPLICATE KEY UPDATE request_key=VALUES(request_key)")
    int ensureCheckoutLock(@Param("user")Long user,@Param("key")String key);
    @Select("SELECT request_key FROM reservation_checkout_request_lock WHERE customer_user_id=#{user} AND request_key=#{key} FOR UPDATE")
    String lockCheckoutKey(@Param("user")Long user,@Param("key")String key);
    @Select("SELECT * FROM reservation_checkout_session WHERE customer_user_id=#{user} AND request_key=#{key} FOR UPDATE")
    ReservationCheckoutSession checkoutByKey(@Param("user")Long user,@Param("key")String key);
    @Select("SELECT * FROM reservation_checkout_session WHERE id=#{id}") ReservationCheckoutSession checkout(Long id);
    @Select("SELECT * FROM reservation_checkout_session WHERE id=#{id} FOR UPDATE") ReservationCheckoutSession lockCheckout(Long id);
    @Insert("""
      INSERT INTO reservation_checkout_session(customer_user_id,room_type_id,check_in_date,check_out_date,guest_count,currency,quoted_total,reservation_policy_id,request_key,status,expires_at)
      VALUES(#{customerUserId},#{roomTypeId},#{checkInDate},#{checkOutDate},#{guestCount},#{currency},#{quotedTotal},#{reservationPolicyId},#{requestKey},#{status},#{expiresAt})
      """) @Options(useGeneratedKeys=true,keyProperty="id") int insertCheckout(ReservationCheckoutSession value);
    @Insert("""
      INSERT INTO reservation_checkout_nightly_quote(checkout_session_id,stay_date,room_type_id,rate_amount,rate_source,dynamic_policy_id,manual_override_id)
      VALUES(#{checkoutSessionId},#{stayDate},#{roomTypeId},#{rateAmount},#{rateSource},#{dynamicPolicyId},#{manualOverrideId})
      """) int insertNight(CheckoutNightlyQuote value);
    @Select("SELECT * FROM reservation_checkout_nightly_quote WHERE checkout_session_id=#{id} ORDER BY stay_date") List<CheckoutNightlyQuote> nights(Long id);
    @Update("UPDATE reservation_checkout_session SET status=#{status} WHERE id=#{id} AND status=#{expected}")
    int checkoutStatus(@Param("id")Long id,@Param("expected")String expected,@Param("status")String status);

    @Select("SELECT * FROM payment_attempt WHERE checkout_session_id=#{checkout} AND request_key=#{key} FOR UPDATE")
    PaymentAttempt attemptByKey(@Param("checkout")Long checkout,@Param("key")String key);
    @Select("SELECT * FROM payment_attempt WHERE id=#{id}") PaymentAttempt attempt(Long id);
    @Select("SELECT * FROM payment_attempt WHERE id=#{id} FOR UPDATE") PaymentAttempt lockAttempt(Long id);
    @Select("SELECT * FROM payment_attempt WHERE merchant_payment_no=#{merchant} FOR UPDATE") PaymentAttempt lockAttemptByMerchant(String merchant);
    @Insert("""
      INSERT INTO payment_attempt(checkout_session_id,purpose,provider,request_key,merchant_payment_no,provider_payment_id,amount,currency,status,fulfillment_status,initiator_type,initiated_by_user_id)
      VALUES(#{checkoutSessionId},#{purpose},#{provider},#{requestKey},#{merchantPaymentNo},#{providerPaymentId},#{amount},#{currency},#{status},#{fulfillmentStatus},#{initiatorType},#{initiatedByUserId})
      """) @Options(useGeneratedKeys=true,keyProperty="id") int insertAttempt(PaymentAttempt value);
    @Update("UPDATE payment_attempt SET status=#{status},provider_payment_id=#{providerId} WHERE id=#{id} AND status='CREATED'")
    int initializeAttempt(@Param("id")Long id,@Param("status")String status,@Param("providerId")String providerId);
    @Update("""
      UPDATE payment_attempt SET status='SUCCEEDED',provider_payment_id=#{providerId},completed_at=NOW(6),recognized_business_date=COALESCE(recognized_business_date,#{businessDate}),failure_code=NULL,failure_message=NULL
      WHERE id=#{id} AND status IN ('CREATED','PENDING','REQUIRES_ACTION','PROCESSING')
      """) int succeed(@Param("id")Long id,@Param("providerId")String providerId,@Param("businessDate")java.time.LocalDate businessDate);
    @Update("""
      UPDATE payment_attempt SET status=#{status},provider_payment_id=COALESCE(provider_payment_id,#{providerId}),completed_at=NOW(6),failure_code=#{code},failure_message=#{message}
      WHERE id=#{id} AND status IN ('CREATED','PENDING','REQUIRES_ACTION','PROCESSING')
      """) int fail(@Param("id")Long id,@Param("providerId")String providerId,@Param("status")String status,@Param("code")String code,@Param("message")String message);
    @Update("UPDATE payment_attempt SET fulfillment_status='COMPLETED',booking_id=#{booking} WHERE id=#{id} AND status='SUCCEEDED' AND fulfillment_status<>'COMPLETED'")
    int fulfilled(@Param("id")Long id,@Param("booking")Long booking);
    @Update("UPDATE payment_attempt SET booking_id=#{booking} WHERE id=#{id} AND status='SUCCEEDED' AND booking_id IS NULL")
    int linkBooking(@Param("id")Long id,@Param("booking")Long booking);
    @Update("UPDATE payment_attempt SET fulfillment_status='FAILED_RETRYABLE',failure_code='FULFILLMENT_RETRY',failure_message=#{message} WHERE id=#{id} AND status='SUCCEEDED' AND fulfillment_status<>'COMPLETED'")
    int retryable(@Param("id")Long id,@Param("message")String message);
    @Select("""
      SELECT id FROM payment_attempt
      WHERE recovery_status='ACTIVE' AND (recovery_claim_until IS NULL OR recovery_claim_until<#{now})
        AND (next_recovery_at IS NULL OR next_recovery_at<=#{now})
        AND ((status='SUCCEEDED' AND fulfillment_status='FAILED_RETRYABLE')
          OR (status IN ('PENDING','PROCESSING','REQUIRES_ACTION') AND updated_at<=#{queryBefore}))
      ORDER BY COALESCE(next_recovery_at,updated_at),id LIMIT #{limit}
      """) List<Long> recoveryCandidates(@Param("now")LocalDateTime now,@Param("queryBefore")LocalDateTime queryBefore,@Param("limit")int limit);
    @Update("""
      UPDATE payment_attempt SET recovery_claim_token=#{token},recovery_claim_until=#{until}
      WHERE id=#{id} AND recovery_status='ACTIVE' AND (recovery_claim_until IS NULL OR recovery_claim_until<#{now})
        AND (next_recovery_at IS NULL OR next_recovery_at<=#{now})
        AND ((status='SUCCEEDED' AND fulfillment_status='FAILED_RETRYABLE')
          OR (status IN ('PENDING','PROCESSING','REQUIRES_ACTION') AND updated_at<=#{queryBefore}))
      """) int claimRecovery(@Param("id")Long id,@Param("token")String token,@Param("now")LocalDateTime now,
          @Param("until")LocalDateTime until,@Param("queryBefore")LocalDateTime queryBefore);
    @Select("SELECT * FROM payment_attempt WHERE id=#{id} AND recovery_claim_token=#{token} FOR UPDATE")
    PaymentAttempt lockClaimed(@Param("id")Long id,@Param("token")String token);
    @Select("SELECT * FROM payment_attempt WHERE id=#{id} AND recovery_claim_token=#{token}")
    PaymentAttempt claimed(@Param("id")Long id,@Param("token")String token);
    @Update("""
      UPDATE payment_attempt SET recovery_claim_token=NULL,recovery_claim_until=NULL,last_recovery_at=#{now},
        recovery_retry_count=0,next_recovery_at=NULL,last_recovery_error=NULL
      WHERE id=#{id} AND recovery_claim_token=#{token}
      """) int recoverySucceeded(@Param("id")Long id,@Param("token")String token,@Param("now")LocalDateTime now);
    @Update("""
      UPDATE payment_attempt SET recovery_claim_token=NULL,recovery_claim_until=NULL,last_recovery_at=#{now},
        recovery_retry_count=recovery_retry_count+1,next_recovery_at=#{next},last_recovery_error=#{error}
      WHERE id=#{id} AND recovery_claim_token=#{token}
      """) int recoveryFailed(@Param("id")Long id,@Param("token")String token,@Param("now")LocalDateTime now,
          @Param("next")LocalDateTime next,@Param("error")String error);
    @Update("""
      UPDATE payment_attempt SET recovery_status='MANUAL_REVIEW',recovery_claim_token=NULL,recovery_claim_until=NULL,
        last_recovery_at=#{now},next_recovery_at=NULL,last_recovery_error=#{error}
      WHERE id=#{id} AND recovery_claim_token=#{token}
      """) int recoveryManualReview(@Param("id")Long id,@Param("token")String token,@Param("now")LocalDateTime now,@Param("error")String error);
    @Update("""
      UPDATE payment_attempt SET status=#{status},provider_payment_id=COALESCE(provider_payment_id,#{providerId}),
        completed_at=CASE WHEN #{status} IN ('FAILED','CANCELLED','EXPIRED') THEN #{occurredAt} ELSE completed_at END,
        failure_code=CASE WHEN #{status} IN ('FAILED','CANCELLED','EXPIRED') THEN CONCAT('PROVIDER_',#{status}) ELSE failure_code END,
        failure_message=CASE WHEN #{status} IN ('FAILED','CANCELLED','EXPIRED') THEN CONCAT('Provider reported ',LOWER(#{status})) ELSE failure_message END
      WHERE id=#{id} AND status IN ('CREATED','PENDING','REQUIRES_ACTION','PROCESSING')
      """) int applyNonSuccess(@Param("id")Long id,@Param("providerId")String providerId,@Param("status")String status,@Param("occurredAt")LocalDateTime occurredAt);

    @Insert("""
      INSERT INTO payment_webhook_event(provider,provider_event_id,event_type,provider_payment_id,payload_hash,processing_status)
      VALUES(#{provider},#{eventId},#{eventType},#{providerPaymentId},#{hash},'RECEIVED')
      ON DUPLICATE KEY UPDATE provider_event_id=VALUES(provider_event_id)
      """) int ensureEvent(@Param("provider")String provider,@Param("eventId")String eventId,@Param("eventType")String eventType,@Param("providerPaymentId")String providerPaymentId,@Param("hash")String hash);
    @Select("SELECT payload_hash FROM payment_webhook_event WHERE provider=#{provider} AND provider_event_id=#{eventId} FOR UPDATE")
    String lockEventHash(@Param("provider")String provider,@Param("eventId")String eventId);
    @Update("UPDATE payment_webhook_event SET processing_status=#{status},processed_at=NOW(6) WHERE provider=#{provider} AND provider_event_id=#{eventId}")
    int eventStatus(@Param("provider")String provider,@Param("eventId")String eventId,@Param("status")String status);

    @Insert("""
      INSERT INTO hotel_transaction_record(direction,amount,currency,source_type,source_id,business_reference_type,business_reference_id,payment_method,channel,provider,provider_transaction_id,initiator_type,operator_user_id,description,occurred_at,business_date)
      VALUES(#{direction},#{amount},#{currency},#{sourceType},#{sourceId},#{businessReferenceType},#{businessReferenceId},#{paymentMethod},#{channel},#{provider},#{providerTransactionId},#{initiatorType},#{operatorUserId},#{description},#{occurredAt},#{businessDate})
      """) @Options(useGeneratedKeys=true,keyProperty="id") int insertTransaction(HotelTransactionRecord value);

    @Select("<script>SELECT * FROM hotel_transaction_record <where>"+
      "<if test='from!=null'>occurred_at &gt;= #{from}</if><if test='to!=null'> AND occurred_at &lt; #{to}</if>"+
      "<if test='direction!=null'> AND direction=#{direction}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if>"+
      "<if test='method!=null'> AND payment_method=#{method}</if><if test='provider!=null'> AND provider=#{provider}</if>"+
      "<if test='currency!=null'> AND currency=#{currency}</if><if test='businessType!=null'> AND business_reference_type=#{businessType}</if>"+
      "<if test='businessId!=null'> AND business_reference_id=#{businessId}</if><if test='providerRef!=null'> AND provider_transaction_id=#{providerRef}</if>"+
      "</where> ORDER BY occurred_at DESC,id DESC LIMIT #{offset},#{size}</script>")
    List<HotelTransactionRecord> transactionPage(@Param("from")LocalDateTime from,@Param("to")LocalDateTime to,@Param("direction")String direction,
      @Param("sourceType")String sourceType,@Param("method")String method,@Param("provider")String provider,@Param("currency")String currency,
      @Param("businessType")String businessType,@Param("businessId")Long businessId,@Param("providerRef")String providerRef,@Param("offset")int offset,@Param("size")int size);
    @Select("<script>SELECT COUNT(*) FROM hotel_transaction_record <where>"+
      "<if test='from!=null'>occurred_at &gt;= #{from}</if><if test='to!=null'> AND occurred_at &lt; #{to}</if>"+
      "<if test='direction!=null'> AND direction=#{direction}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if>"+
      "<if test='method!=null'> AND payment_method=#{method}</if><if test='provider!=null'> AND provider=#{provider}</if>"+
      "<if test='currency!=null'> AND currency=#{currency}</if><if test='businessType!=null'> AND business_reference_type=#{businessType}</if>"+
      "<if test='businessId!=null'> AND business_reference_id=#{businessId}</if><if test='providerRef!=null'> AND provider_transaction_id=#{providerRef}</if>"+
      "</where></script>")
    long transactionCount(@Param("from")LocalDateTime from,@Param("to")LocalDateTime to,@Param("direction")String direction,
      @Param("sourceType")String sourceType,@Param("method")String method,@Param("provider")String provider,@Param("currency")String currency,
      @Param("businessType")String businessType,@Param("businessId")Long businessId,@Param("providerRef")String providerRef);
}
