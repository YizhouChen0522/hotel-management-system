package com.johnny.hotel.finance;import org.apache.ibatis.annotations.*;import java.time.*;import java.util.*;
@Mapper public interface RevenueRecognitionMapper {
 @Insert("""
 INSERT IGNORE INTO revenue_recognition(revenue_date,recognition_business_date,category,amount,source_type,source_id,stay_id,folio_id,created_at)
 SELECT i.business_date,#{closingDate},CASE WHEN i.amount<0 THEN 'ADJUSTMENT' WHEN i.item_type='ROOM_CHARGE' THEN 'ROOM_REVENUE' ELSE 'OTHER_REVENUE' END,
        i.amount,'FOLIO_ITEM',i.id,f.stay_id,f.id,#{at}
 FROM folio_item i JOIN folio f ON f.id=i.folio_id WHERE i.business_date<=#{revenueDate}
 """) int recognizeFolioItems(@Param("revenueDate")LocalDate revenueDate,@Param("closingDate")LocalDate closingDate,@Param("at")LocalDateTime at);
 @Select("SELECT * FROM revenue_recognition WHERE recognition_business_date=#{date} ORDER BY id") List<RevenueRecognition> byRecognitionDate(LocalDate date);
 @Select("SELECT * FROM revenue_recognition WHERE revenue_date=#{date} ORDER BY id") List<RevenueRecognition> byRevenueDate(LocalDate date);
 @Insert("""
 INSERT INTO daily_financial_summary(business_date,room_revenue,other_revenue,adjustments,total_recognized_revenue,external_cash_inflow,external_cash_outflow,night_audit_run_id,created_at)
 SELECT #{date},
   COALESCE(SUM(CASE WHEN category='ROOM_REVENUE' THEN amount ELSE 0 END),0),
   COALESCE(SUM(CASE WHEN category='OTHER_REVENUE' THEN amount ELSE 0 END),0),
   COALESCE(SUM(CASE WHEN category='ADJUSTMENT' THEN amount ELSE 0 END),0),
   COALESCE(SUM(amount),0),
   COALESCE((SELECT SUM(amount) FROM hotel_transaction_record WHERE business_date=#{date} AND direction='EXTERNAL_IN'),0),
   COALESCE((SELECT SUM(amount) FROM hotel_transaction_record WHERE business_date=#{date} AND direction='EXTERNAL_OUT'),0),
   #{run},#{at}
 FROM revenue_recognition WHERE recognition_business_date=#{date}
 """) int insertSummary(@Param("date")LocalDate date,@Param("run")Long run,@Param("at")LocalDateTime at);
 @Select("SELECT * FROM daily_financial_summary WHERE business_date=#{date}") DailyFinancialSummary summary(LocalDate date);
 @Select("SELECT * FROM daily_financial_summary ORDER BY business_date DESC LIMIT 100") List<DailyFinancialSummary> summaries();
}
