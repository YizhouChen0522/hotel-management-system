package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.RoomType;
import org.apache.ibatis.annotations.*;
import com.johnny.hotel.entity.Room;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface RoomMapper {
    @Insert("""
            INSERT INTO room (
                room_number,
                room_type_id,
                floor,
                status,
                create_time,
                update_time
            )
            VALUES (
                #{roomNumber},
                #{roomTypeId},
                #{floor},
                #{status},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Room room);

    @Select("SELECT * FROM room WHERE id = #{id} ")
    Room selectById(@Param("id") Long id);

    @Select("SELECT * FROM room WHERE room_number = #{roomNumber} ")
    Room selectByRoomNumber(@Param("roomNumber") String roomNumber);

    @Select("SELECT * FROM room ORDER BY id DESC")
    List<Room> selectAll();

    @Select("SELECT * FROM room WHERE floor = #{floor} ORDER BY id DESC")
    List<Room> selectByFloor(@Param("floor") Integer floor);

    @Update("""
        UPDATE room
        SET room_number = #{roomNumber},
            room_type_id = #{roomTypeId},
            floor = #{floor},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int update(Room room);

@Select("""
        SELECT *
        FROM room
        WHERE id = #{id}
        FOR UPDATE
        """)
    Room selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE room SET status=#{status}, update_time=NOW() WHERE id=#{id} AND status=#{expectedStatus}")
    int transitionStatus(@Param("id") Long id, @Param("expectedStatus") Integer expectedStatus, @Param("status") Integer status);

    // Nonlocking existence reads while Room is locked. All legitimate assignments acquire Room first.
    @Select("SELECT EXISTS(SELECT 1 FROM booking WHERE assigned_room_id=#{id} AND status IN (1,2)) OR EXISTS(SELECT 1 FROM booking_room_assignment WHERE room_id=#{id} AND end_time IS NULL)")
    boolean hasLiveUse(@Param("id") Long id);

}
