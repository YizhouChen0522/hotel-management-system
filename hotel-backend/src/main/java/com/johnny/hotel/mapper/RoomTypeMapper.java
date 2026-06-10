package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.RoomType;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoomTypeMapper {
    @Insert("""
            INSERT INTO room_type (
                type_name,
                description,
                base_price,
                capacity,
                status,
                create_time,
                update_time
            )
            VALUES (
                #{typeName},
                #{description},
                #{basePrice},
                #{capacity},
                #{status},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RoomType roomType);

    @Select("SELECT * FROM room_type WHERE id = #{id} ")
    RoomType selectById(@Param("id") Long id);

    @Select("SELECT * FROM room_type ORDER BY id DESC")
    List<RoomType> selectAll();

    @Select("SELECT * FROM room_type WHERE type_name = #{typeName} ")
    RoomType selectByTypeName(@Param("typeName") String typeName);

    @Update("""
        UPDATE room_type
        SET type_name = #{typeName},
            description = #{description},
            base_price = #{basePrice},
            capacity = #{capacity},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int update(RoomType roomType);

    @Update("""
            UPDATE room_type
            SET status = #{status},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
