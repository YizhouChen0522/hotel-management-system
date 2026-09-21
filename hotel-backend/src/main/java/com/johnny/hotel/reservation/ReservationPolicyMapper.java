package com.johnny.hotel.reservation;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper public interface ReservationPolicyMapper {
    @Select("SELECT * FROM reservation_policy WHERE status=1 LIMIT 1") ReservationPolicy active();
    @Select("SELECT * FROM reservation_policy WHERE id=#{id}") ReservationPolicy find(Long id);
    @Select("SELECT * FROM reservation_policy WHERE id=#{id} FOR UPDATE") ReservationPolicy lock(Long id);
    @Select("SELECT * FROM reservation_policy ORDER BY version_no DESC LIMIT 100") List<ReservationPolicy> list();
    @Select("SELECT * FROM reservation_policy_band WHERE policy_id=#{id} ORDER BY min_lead_days") List<ReservationPolicyBand> bands(Long id);
    @Select("SELECT COALESCE(MAX(version_no),0)+1 FROM reservation_policy") long nextVersion();
    @Select("SELECT id FROM reservation_policy_lock WHERE id=1 FOR UPDATE") Integer lockVersions();
    @Insert("INSERT INTO reservation_policy(version_no,name,status,created_by) VALUES(#{versionNo},#{name},0,#{createdBy})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(ReservationPolicy policy);
    @Insert("INSERT INTO reservation_policy_band(policy_id,min_lead_days,max_lead_days,refund_percent) VALUES(#{policyId},#{minLeadDays},#{maxLeadDays},#{refundPercent})") int insertBand(ReservationPolicyBand band);
    @Delete("DELETE FROM reservation_policy_band WHERE policy_id=#{id} AND EXISTS(SELECT 1 FROM reservation_policy WHERE id=#{id} AND status=0)") int deleteDraftBands(Long id);
    @Update("UPDATE reservation_policy SET name=#{name} WHERE id=#{id} AND status=0") int updateDraft(@Param("id")Long id,@Param("name")String name);
    @Update("UPDATE reservation_policy SET status=2 WHERE status=1") int disableActive();
    @Update("UPDATE reservation_policy SET status=1,activated_by=#{actor},activated_time=NOW(6) WHERE id=#{id} AND status=0") int activate(@Param("id")Long id,@Param("actor")Long actor);
    @Update("UPDATE reservation_policy SET status=2 WHERE id=#{id} AND status=1") int disable(@Param("id")Long id);
}
