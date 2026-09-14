package com.johnny.hotel.workorder;
import org.apache.ibatis.annotations.*;import java.math.BigDecimal;import java.util.List;
@Mapper public interface RoomWorkOrderMapper {
 @Insert("INSERT INTO room_work_order(task_id,room_id,reported_by,request_key,damage_type,description,severity,affects_sellability,blocks_room_release,status,estimated_cost) VALUES(#{taskId},#{roomId},#{reportedBy},#{requestKey},#{damageType},#{description},#{severity},#{affectsSellability},#{blocksRoomRelease},0,#{estimatedCost})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(RoomWorkOrder w);
 @Select("SELECT * FROM room_work_order WHERE id=#{id}") RoomWorkOrder find(Long id);
 @Select("SELECT * FROM room_work_order WHERE id=#{id} FOR UPDATE") RoomWorkOrder lock(Long id);
 @Select("SELECT * FROM room_work_order WHERE task_id=#{id} FOR UPDATE") RoomWorkOrder byTaskForUpdate(Long id);
 @Select("SELECT * FROM room_work_order WHERE reported_by=#{actor} AND request_key=#{key}") RoomWorkOrder byRequest(@Param("actor")Long actor,@Param("key")String key);
 @Select("<script>SELECT * FROM room_work_order <where><if test='status!=null'>status=#{status}</if><if test='room!=null'> AND room_id=#{room}</if></where> ORDER BY id DESC LIMIT #{offset},#{size}</script>") List<RoomWorkOrder> page(@Param("status")Integer status,@Param("room")Long room,@Param("offset")int offset,@Param("size")int size);
 @Update("UPDATE room_work_order SET status=1,actual_cost=#{cost},resolved_time=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status=0") int resolve(@Param("id")Long id,@Param("cost")BigDecimal cost);
 @Update("UPDATE room_work_order SET status=2,cancelled_time=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status=0") int cancel(Long id);
 @Select("SELECT EXISTS(SELECT 1 FROM room_work_order WHERE room_id=#{id} AND status=0 AND blocks_room_release=1)") boolean hasOpenBlockingOrder(Long id);
}
