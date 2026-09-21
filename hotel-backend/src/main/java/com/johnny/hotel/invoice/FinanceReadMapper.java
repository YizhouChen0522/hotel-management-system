package com.johnny.hotel.invoice;import org.apache.ibatis.annotations.*;import java.util.*;
@Mapper public interface FinanceReadMapper{
 @Select("SELECT * FROM booking ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> bookings(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM stay_room_assignment ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> assignments(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM room ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> rooms(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM stay ORDER BY actual_check_in_time DESC,id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> stays(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT id,linked_user_id,first_name,last_name,status,create_time,update_time FROM guest_profile ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> guests(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM cleaning_record ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> cleaning(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM housekeeping_inspection ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> inspections(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM room_work_order ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> workOrders(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM room_turnover_task ORDER BY id DESC LIMIT #{offset},#{size}")List<Map<String,Object>> turnovers(@Param("offset")int offset,@Param("size")int size);
}
