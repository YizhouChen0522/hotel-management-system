package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.StayHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface StayHistoryMapper {
 String ROWS="SELECT h.*,a.start_time AS actual_check_in_time,a.end_time AS actual_check_out_time FROM stay_history h JOIN stay s ON s.id=h.stay_id JOIN booking b ON b.id=s.booking_id JOIN stay_room_assignment a ON a.id=h.assignment_id ";
 String COUNT="SELECT COUNT(*) FROM stay_history h JOIN stay s ON s.id=h.stay_id JOIN booking b ON b.id=s.booking_id ";
 @Insert("INSERT INTO stay_history(stay_id,folio_id,assignment_id,room_number,room_type_name) VALUES(#{stayId},#{folioId},#{assignmentId},#{roomNumber},#{roomTypeName})")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(StayHistory history);
 @Select(ROWS+"WHERE b.user_id=#{userId} ORDER BY a.start_time DESC,h.id DESC") List<StayHistory> findByUserId(Long userId);
 @Select(ROWS+"WHERE b.user_id=#{userId} ORDER BY a.start_time DESC,h.id DESC LIMIT #{offset},#{size}") List<StayHistory> pageByUser(@Param("userId")Long userId,@Param("offset")int offset,@Param("size")int size);
 @Select(COUNT+"WHERE b.user_id=#{userId}") long countByUser(Long userId);
 @Select(ROWS+"WHERE h.folio_id=#{folioId} AND b.user_id=#{userId} ORDER BY a.start_time DESC,h.id DESC") List<StayHistory> findByFolioIdAndUserId(@Param("folioId")Long folioId,@Param("userId")Long userId);
 @Select(ROWS+"WHERE h.folio_id=#{folioId} AND b.user_id=#{userId} ORDER BY a.start_time DESC,h.id DESC LIMIT #{offset},#{size}") List<StayHistory> pageByFolioUser(@Param("folioId")Long folioId,@Param("userId")Long userId,@Param("offset")int offset,@Param("size")int size);
 @Select(COUNT+"WHERE h.folio_id=#{folioId} AND b.user_id=#{userId}") long countByFolioUser(@Param("folioId")Long folioId,@Param("userId")Long userId);
 @Select("SELECT COUNT(*) FROM stay_history WHERE assignment_id=#{assignmentId}") int countByAssignmentId(Long assignmentId);
 @Select("""
 SELECT a.stay_id,f.id AS folio_id,a.id AS assignment_id,r.room_number,rt.type_name AS room_type_name
 FROM stay_room_assignment a JOIN folio f ON f.stay_id=a.stay_id
 JOIN room r ON r.id=a.room_id JOIN room_type rt ON rt.id=a.room_type_id
 WHERE a.stay_id=#{stayId} AND a.end_time IS NOT NULL ORDER BY a.start_time,a.id
 """) List<StayHistory> findCompletedAssignmentSnapshots(Long stayId);
}
