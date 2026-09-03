package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.FolioItem;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface FolioItemMapper {

    @Insert("""
            INSERT INTO folio_item
            (
                folio_id,
                item_type,
                description,
                business_date,
                quantity,
                unit_price,
                amount,
                room_id,
                room_type_id,
                room_assignment_id,
                source_item_id,
                refundable,
                created_by
            )
            VALUES
            (
                #{folioId},
                #{itemType},
                #{description},
                #{businessDate},
                #{quantity},
                #{unitPrice},
                #{amount},
                #{roomId},
                #{roomTypeId},
                #{roomAssignmentId},
                #{sourceItemId},
                #{refundable},
                #{createdBy}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    int insert(FolioItem item);


    @Select("""
            SELECT *
            FROM folio_item
            WHERE id = #{id}
            """)
    FolioItem selectById(
            @Param("id") Long id
    );


    @Select("""
            SELECT *
            FROM folio_item
            WHERE folio_id = #{folioId}
            ORDER BY create_time ASC, id ASC
            """)
    List<FolioItem> selectByFolioId(
            @Param("folioId") Long folioId
    );


    @Select("""
            SELECT COALESCE(SUM(amount), 0.00)
            FROM folio_item
            WHERE folio_id = #{folioId}
            """)
    BigDecimal sumAmountByFolioId(
            @Param("folioId") Long folioId
    );
    @Select("""
        SELECT *
        FROM folio_item
        WHERE folio_id = #{folioId}
          AND room_assignment_id = #{assignmentId}
          AND item_type = 'ROOM_CHARGE'
          AND business_date >= #{fromDate}
        ORDER BY business_date ASC, id ASC
        """)
    List<FolioItem> selectRoomChargesFromDate(
            @Param("folioId") Long folioId,
            @Param("assignmentId") Long assignmentId,
            @Param("fromDate") LocalDate fromDate
    );

    @Select("""
        SELECT COUNT(*)
        FROM folio_item
        WHERE folio_id = #{folioId}
        """)
    int countByFolioId(
            @Param("folioId") Long folioId
    );
}