package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.Folio;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;

@Mapper
public interface FolioMapper {

    @Insert("""
            INSERT INTO folio
            (
                booking_id,
                status,
                currency,
                total_amount,
                paid_amount,
                balance_amount
            )
            VALUES
            (
                #{bookingId},
                #{status},
                #{currency},
                #{totalAmount},
                #{paidAmount},
                #{balanceAmount}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    int insert(Folio folio);


    @Select("""
            SELECT *
            FROM folio
            WHERE id = #{id}
            """)
    Folio selectById(
            @Param("id") Long id
    );


    @Select("""
            SELECT *
            FROM folio
            WHERE booking_id = #{bookingId}
            """)
    Folio selectByBookingId(
            @Param("bookingId") Long bookingId
    );


    @Select("""
            SELECT *
            FROM folio
            WHERE booking_id = #{bookingId}
            FOR UPDATE
            """)
    Folio selectByBookingIdForUpdate(
            @Param("bookingId") Long bookingId
    );


    @Update("""
            UPDATE folio
            SET total_amount = #{totalAmount},
                paid_amount = #{paidAmount},
                balance_amount = #{balanceAmount},
                status = #{status},
                settled_time = #{settledTime},
                update_time = NOW()
            WHERE id = #{folioId}
            """)
    int updateFinancialSummary(
            @Param("folioId") Long folioId,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("balanceAmount") BigDecimal balanceAmount,
            @Param("status") String status,
            @Param("settledTime") java.time.LocalDateTime settledTime
    );
}
