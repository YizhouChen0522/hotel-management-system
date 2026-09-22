package com.johnny.hotel.businessdate;
import org.apache.ibatis.annotations.*;
import java.time.*;
@Mapper public interface BusinessDateMapper {
 @Select("SELECT * FROM hotel_business_date_control WHERE id=1") BusinessDateControl current();
 @Select("SELECT * FROM hotel_business_date_control WHERE id=1 FOR UPDATE") BusinessDateControl lockExclusive();
 @Select("SELECT * FROM hotel_business_date_control WHERE id=1 LOCK IN SHARE MODE") BusinessDateControl lockForPosting();
 @Update("UPDATE hotel_business_date_control SET business_date=#{date},state='OPEN',initialized_at=#{at},version=version+1 WHERE id=1 AND business_date IS NULL") int initialize(@Param("date")LocalDate date,@Param("at")LocalDateTime at);
 @Insert("INSERT INTO hotel_business_date_history(event_type,old_business_date,new_business_date,state,operator_user_id,occurred_at,initialization_key) VALUES('INITIALIZED',NULL,#{date},'OPEN',NULL,#{at},1)") int history(@Param("date")LocalDate date,@Param("at")LocalDateTime at);
 @Select("SELECT COUNT(*) FROM hotel_business_date_history WHERE event_type='INITIALIZED'") int initializationCount();
}
