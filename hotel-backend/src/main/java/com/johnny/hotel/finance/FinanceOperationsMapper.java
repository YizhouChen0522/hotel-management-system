package com.johnny.hotel.finance;

import com.johnny.hotel.finance.FinanceOperationsModels.*;
import com.johnny.hotel.payment.HotelTransactionRecord;
import org.apache.ibatis.annotations.*;
import java.time.*;import java.util.*;

@Mapper public interface FinanceOperationsMapper {
 @Select("""
 SELECT #{from} from_business_date,#{to} to_business_date,
  COALESCE(SUM(room_revenue),0) room_revenue,COALESCE(SUM(other_revenue),0) other_revenue,
  COALESCE(SUM(adjustments),0) negative_adjustments,COALESCE(SUM(total_recognized_revenue),0) total_recognized_revenue,
  COALESCE(SUM(external_cash_inflow),0) external_inflow,COALESCE(SUM(external_cash_outflow),0) external_outflow,
  COALESCE(SUM(external_cash_inflow-external_cash_outflow),0) net_external_cash_movement,
  'REGISTERED_HOTEL_TRANSACTION_RECORDS_ONLY' money_movement_coverage
 FROM daily_financial_summary WHERE business_date BETWEEN #{from} AND #{to}
 """) Dashboard dashboard(@Param("from")LocalDate from,@Param("to")LocalDate to);

 @Select("SELECT * FROM daily_financial_summary WHERE business_date BETWEEN #{from} AND #{to} ORDER BY business_date DESC,id DESC LIMIT #{offset},#{size}")
 List<DailyFinancialSummary> summaryPage(@Param("from")LocalDate from,@Param("to")LocalDate to,@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT COUNT(*) FROM daily_financial_summary WHERE business_date BETWEEN #{from} AND #{to}") long summaryCount(@Param("from")LocalDate from,@Param("to")LocalDate to);

 @Select("<script>SELECT * FROM revenue_recognition <where>"+
  "<if test='revenueFrom!=null'>revenue_date &gt;= #{revenueFrom}</if><if test='revenueTo!=null'> AND revenue_date &lt;= #{revenueTo}</if>"+
  "<if test='recognitionFrom!=null'> AND recognition_business_date &gt;= #{recognitionFrom}</if><if test='recognitionTo!=null'> AND recognition_business_date &lt;= #{recognitionTo}</if>"+
  "<if test='category!=null'> AND category=#{category}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if>"+
  "<if test='stayId!=null'> AND stay_id=#{stayId}</if><if test='folioId!=null'> AND folio_id=#{folioId}</if>"+
  "</where> ORDER BY recognition_business_date DESC,revenue_date DESC,id DESC LIMIT #{offset},#{size}</script>")
 List<RevenueRecognition> revenuePage(@Param("revenueFrom")LocalDate revenueFrom,@Param("revenueTo")LocalDate revenueTo,@Param("recognitionFrom")LocalDate recognitionFrom,@Param("recognitionTo")LocalDate recognitionTo,@Param("category")String category,@Param("sourceType")String sourceType,@Param("stayId")Long stayId,@Param("folioId")Long folioId,@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT COUNT(*) FROM revenue_recognition <where>"+
  "<if test='revenueFrom!=null'>revenue_date &gt;= #{revenueFrom}</if><if test='revenueTo!=null'> AND revenue_date &lt;= #{revenueTo}</if>"+
  "<if test='recognitionFrom!=null'> AND recognition_business_date &gt;= #{recognitionFrom}</if><if test='recognitionTo!=null'> AND recognition_business_date &lt;= #{recognitionTo}</if>"+
  "<if test='category!=null'> AND category=#{category}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if>"+
  "<if test='stayId!=null'> AND stay_id=#{stayId}</if><if test='folioId!=null'> AND folio_id=#{folioId}</if>"+
  "</where></script>")
 long revenueCount(@Param("revenueFrom")LocalDate revenueFrom,@Param("revenueTo")LocalDate revenueTo,@Param("recognitionFrom")LocalDate recognitionFrom,@Param("recognitionTo")LocalDate recognitionTo,@Param("category")String category,@Param("sourceType")String sourceType,@Param("stayId")Long stayId,@Param("folioId")Long folioId);

 @Select("<script>SELECT * FROM hotel_transaction_record <where>"+
  "<if test='businessFrom!=null'>business_date &gt;= #{businessFrom}</if><if test='businessTo!=null'> AND business_date &lt;= #{businessTo}</if>"+
  "<if test='occurredFrom!=null'> AND occurred_at &gt;= #{occurredFrom}</if><if test='occurredTo!=null'> AND occurred_at &lt; #{occurredTo}</if>"+
  "<if test='direction!=null'> AND direction=#{direction}</if><if test='method!=null'> AND payment_method=#{method}</if>"+
  "<if test='provider!=null'> AND provider=#{provider}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if>"+
  "<if test='businessType!=null'> AND business_reference_type=#{businessType}</if><if test='businessId!=null'> AND business_reference_id=#{businessId}</if>"+
  "</where> ORDER BY business_date DESC,occurred_at DESC,id DESC LIMIT #{offset},#{size}</script>")
 List<HotelTransactionRecord> movementPage(@Param("businessFrom")LocalDate businessFrom,@Param("businessTo")LocalDate businessTo,@Param("occurredFrom")LocalDateTime occurredFrom,@Param("occurredTo")LocalDateTime occurredTo,@Param("direction")String direction,@Param("method")String method,@Param("provider")String provider,@Param("sourceType")String sourceType,@Param("businessType")String businessType,@Param("businessId")Long businessId,@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT COUNT(*) FROM hotel_transaction_record <where>"+
  "<if test='businessFrom!=null'>business_date &gt;= #{businessFrom}</if><if test='businessTo!=null'> AND business_date &lt;= #{businessTo}</if>"+
  "<if test='occurredFrom!=null'> AND occurred_at &gt;= #{occurredFrom}</if><if test='occurredTo!=null'> AND occurred_at &lt; #{occurredTo}</if>"+
  "<if test='direction!=null'> AND direction=#{direction}</if><if test='method!=null'> AND payment_method=#{method}</if>"+
  "<if test='provider!=null'> AND provider=#{provider}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if>"+
  "<if test='businessType!=null'> AND business_reference_type=#{businessType}</if><if test='businessId!=null'> AND business_reference_id=#{businessId}</if>"+
  "</where></script>")
 long movementCount(@Param("businessFrom")LocalDate businessFrom,@Param("businessTo")LocalDate businessTo,@Param("occurredFrom")LocalDateTime occurredFrom,@Param("occurredTo")LocalDateTime occurredTo,@Param("direction")String direction,@Param("method")String method,@Param("provider")String provider,@Param("sourceType")String sourceType,@Param("businessType")String businessType,@Param("businessId")Long businessId);

 @Select("""
 SELECT * FROM (
  SELECT 'PAYMENT_ATTEMPT' source_type,p.id source_id,
   CASE WHEN p.recovery_status='MANUAL_REVIEW' THEN 'PAYMENT_MANUAL_REVIEW' ELSE 'PAYMENT_FULFILLMENT_INCOMPLETE' END exception_type,
   COALESCE(p.last_recovery_error,p.failure_message,'Successful payment fulfillment is incomplete') reason,
   CONCAT(p.status,'/',p.fulfillment_status) status,p.recognized_business_date business_date,p.completed_at occurred_at,p.updated_at
  FROM payment_attempt p WHERE p.recovery_status='MANUAL_REVIEW' OR (p.status='SUCCEEDED' AND p.fulfillment_status<>'COMPLETED')
  UNION ALL
  SELECT 'NIGHT_AUDIT_RUN',n.id,CONCAT('NIGHT_AUDIT_',n.status),COALESCE(n.last_error,b.reason),n.status,n.business_date,n.started_at,n.update_time
  FROM night_audit_run n LEFT JOIN night_audit_blocker b ON b.run_id=n.id AND b.active=1 WHERE n.status IN ('BLOCKED','FAILED')
  UNION ALL
  SELECT 'GATEWAY_SETTLEMENT_BATCH',s.id,CONCAT('SETTLEMENT_',s.reconciliation_status),
   CONCAT(s.provider,'/',s.provider_batch_id,': ',COALESCE(s.exception_reason,'Unresolved settlement reconciliation')),
   CONCAT(s.status,'/',s.reconciliation_status),s.posting_business_date,s.provider_settled_at,s.update_time
  FROM gateway_settlement_batch s
  WHERE s.status='EXCEPTION' OR s.reconciliation_status IN ('MISSING','MISMATCH')
 ) x ORDER BY updated_at DESC,source_type,source_id LIMIT #{offset},#{size}
 """) List<FinanceException> exceptionPage(@Param("offset")int offset,@Param("size")int size);
 @Select("""
 SELECT (SELECT COUNT(*) FROM payment_attempt WHERE recovery_status='MANUAL_REVIEW' OR (status='SUCCEEDED' AND fulfillment_status<>'COMPLETED'))+
        (SELECT COUNT(*) FROM night_audit_run n LEFT JOIN night_audit_blocker b ON b.run_id=n.id AND b.active=1 WHERE n.status IN ('BLOCKED','FAILED'))
       +(SELECT COUNT(*) FROM gateway_settlement_batch WHERE status='EXCEPTION' OR reconciliation_status IN ('MISSING','MISMATCH'))
 """) long exceptionCount();
}
