package com.johnny.hotel.nightaudit;
import org.apache.ibatis.annotations.*;
import java.time.*;import java.util.*;
@Mapper public interface NightAuditMapper {
 @Select("SELECT * FROM night_audit_run WHERE business_date=#{date}") NightAuditRun byDate(LocalDate date);
 @Select("SELECT * FROM night_audit_run WHERE request_key=#{key}") NightAuditRun byRequest(String key);
 @Select("SELECT * FROM night_audit_run WHERE id=#{id} FOR UPDATE") NightAuditRun lock(Long id);
 @Select("SELECT * FROM night_audit_run ORDER BY business_date DESC,id DESC LIMIT 1") NightAuditRun latest();
 @Select("SELECT * FROM night_audit_run ORDER BY business_date DESC,id DESC LIMIT #{limit}") List<NightAuditRun> history(int limit);
 @Insert("INSERT INTO night_audit_run(business_date,request_key,status,last_step,started_by,started_at) VALUES(#{businessDate},#{requestKey},#{status},#{lastStep},#{startedBy},#{startedAt})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(NightAuditRun run);
 @Update("UPDATE night_audit_run SET processing_token=#{token},processing_until=#{until},status='RUNNING',attempt_count=attempt_count+1,last_error=NULL WHERE id=#{id} AND status<>'COMPLETED' AND (processing_until IS NULL OR processing_until<#{now})") int claim(@Param("id")Long id,@Param("token")String token,@Param("now")LocalDateTime now,@Param("until")LocalDateTime until);
 @Update("UPDATE night_audit_run SET last_step=#{step},processing_until=#{until} WHERE id=#{id} AND processing_token=#{token}") int step(@Param("id")Long id,@Param("token")String token,@Param("step")String step,@Param("until")LocalDateTime until);
 @Update("UPDATE night_audit_run SET status='BLOCKED',last_error=#{error},processing_token=NULL,processing_until=NULL WHERE id=#{id} AND processing_token=#{token}") int blocked(@Param("id")Long id,@Param("token")String token,@Param("error")String error);
 @Update("UPDATE night_audit_run SET status='FAILED',last_error=#{error},processing_token=NULL,processing_until=NULL WHERE id=#{id} AND processing_token=#{token}") int failed(@Param("id")Long id,@Param("token")String token,@Param("error")String error);
 @Update("UPDATE night_audit_run SET status='BLOCKED',last_step='PRECHECK',last_error=#{error},processing_token=NULL,processing_until=NULL WHERE id=#{id}") int precheckBlocked(@Param("id")Long id,@Param("error")String error);
 @Update("UPDATE night_audit_run SET status='STARTING',last_step='BEGIN',last_error=NULL WHERE id=#{id} AND status<>'COMPLETED'") int ready(Long id);
 @Update("UPDATE night_audit_run SET status='COMPLETED',last_step='ADVANCE',completed_at=#{at},last_error=NULL,processing_token=NULL,processing_until=NULL WHERE id=#{id} AND processing_token=#{token} AND status='RUNNING'") int complete(@Param("id")Long id,@Param("token")String token,@Param("at")LocalDateTime at);
 @Update("UPDATE night_audit_blocker SET active=0,resolved_at=#{at} WHERE run_id=#{run} AND active=1") int resolveAll(@Param("run")Long run,@Param("at")LocalDateTime at);
 @Insert("INSERT INTO night_audit_blocker(run_id,blocker_type,object_type,object_id,reason,active,detected_at) VALUES(#{runId},#{blockerType},#{objectType},#{objectId},#{reason},1,#{detectedAt}) ON DUPLICATE KEY UPDATE reason=VALUES(reason),active=1,detected_at=VALUES(detected_at),resolved_at=NULL") int blocker(NightAuditBlocker blocker);
 @Select("SELECT * FROM night_audit_blocker WHERE run_id=#{run} AND active=1 ORDER BY blocker_type,object_type,object_id") List<NightAuditBlocker> blockers(Long run);
 @Select("SELECT id FROM payment_attempt WHERE status='SUCCEEDED' AND recognized_business_date=#{date} AND fulfillment_status<>'COMPLETED' ORDER BY id") List<Long> incompletePayments(LocalDate date);
 @Select("SELECT id FROM booking b WHERE b.status=0 AND b.check_in_date<=#{date} AND NOT EXISTS(SELECT 1 FROM stay s WHERE s.booking_id=b.id) ORDER BY id") List<Long> pendingArrivals(LocalDate date);
 @Select("SELECT id FROM booking b WHERE b.status=1 AND b.check_in_date<=#{date} AND NOT EXISTS(SELECT 1 FROM stay s WHERE s.booking_id=b.id) ORDER BY id") List<Long> approvedArrivals(LocalDate date);
 @Select("SELECT s.id FROM stay s JOIN booking b ON b.id=s.booking_id WHERE s.status=1 AND COALESCE((SELECT a.new_end FROM stay_adjustment a WHERE a.stay_id=s.id ORDER BY a.id DESC LIMIT 1),b.check_out_date)<=#{date} ORDER BY s.id") List<Long> overdueStays(LocalDate date);
}
