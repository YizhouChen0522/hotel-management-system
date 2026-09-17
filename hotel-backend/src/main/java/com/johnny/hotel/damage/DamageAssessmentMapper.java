package com.johnny.hotel.damage;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DamageAssessmentMapper {
 @Insert("INSERT INTO damage_assessment(room_id,stay_id,assignment_id,folio_id,source_type,source_id,damage_description,request_key,reported_by) VALUES(#{roomId},#{stayId},#{assignmentId},#{folioId},#{sourceType},#{sourceId},#{damageDescription},#{requestKey},#{reportedBy})")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(DamageAssessment value);
 @Select("SELECT * FROM damage_assessment WHERE id=#{id}") DamageAssessment find(Long id);
 @Select("SELECT * FROM damage_assessment WHERE id=#{id} FOR UPDATE") DamageAssessment lock(Long id);
 @Select("SELECT * FROM damage_assessment WHERE reported_by=#{actor} AND request_key=#{key}") DamageAssessment byRequest(@Param("actor")Long actor,@Param("key")String key);
 @Select("SELECT * FROM damage_assessment WHERE source_type=#{type} AND source_id=#{source}") DamageAssessment bySource(@Param("type")int type,@Param("source")Long source);
 @Update("UPDATE damage_assessment SET responsibility=#{responsibility},assessed_charge=#{charge},reason=#{reason},status=#{status},folio_item_id=#{item},assessed_by=#{actor},assessed_time=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status=0 AND responsibility=0")
 int resolve(@Param("id")Long id,@Param("responsibility")int responsibility,@Param("charge")BigDecimal charge,@Param("reason")String reason,@Param("status")int status,@Param("item")Long item,@Param("actor")Long actor);
 @Select("SELECT * FROM damage_assessment WHERE folio_id=#{folio} ORDER BY id FOR UPDATE") List<DamageAssessment> forFolio(Long folio);
 @Select("SELECT r.room_id,i.stay_id,i.assignment_id FROM room_repair_task r JOIN housekeeping_inspection i ON i.id=r.inspection_id WHERE r.id=#{id}") DamageSourceFacts repairFacts(Long id);
 @Select("<script>SELECT * FROM damage_assessment <where><if test='booking!=null'>stay_id=#{booking}</if><if test='room!=null'> AND room_id=#{room}</if><if test='status!=null'> AND status=#{status}</if><if test='responsibility!=null'> AND responsibility=#{responsibility}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if></where> ORDER BY create_time DESC,id DESC LIMIT #{offset},#{size}</script>")
 List<DamageAssessment> page(@Param("booking")Long booking,@Param("room")Long room,@Param("status")Integer status,@Param("responsibility")Integer responsibility,@Param("sourceType")Integer sourceType,@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT COUNT(*) FROM damage_assessment <where><if test='booking!=null'>stay_id=#{booking}</if><if test='room!=null'> AND room_id=#{room}</if><if test='status!=null'> AND status=#{status}</if><if test='responsibility!=null'> AND responsibility=#{responsibility}</if><if test='sourceType!=null'> AND source_type=#{sourceType}</if></where></script>")
 long count(@Param("booking")Long booking,@Param("room")Long room,@Param("status")Integer status,@Param("responsibility")Integer responsibility,@Param("sourceType")Integer sourceType);
 record DamageSourceFacts(Long roomId,Long stayId,Long assignmentId){}
}
