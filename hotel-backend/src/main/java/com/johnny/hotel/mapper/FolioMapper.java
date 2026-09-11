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
                currency,
                total_amount,
                paid_amount,
                balance_amount
            )
            VALUES
            (
                #{bookingId},
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


    // Resolve immutable booking -> folio identity without a secondary-index lock, then lock PRIMARY.
    // Otherwise Payment(PRIMARY) -> summary(uk_folio_booking) can deadlock with checkout.
    @Select("""
            SELECT *
            FROM folio
            WHERE id = (SELECT f.id FROM folio f WHERE f.booking_id = #{bookingId})
            FOR UPDATE
            """)
    Folio selectByBookingIdForUpdate(
            @Param("bookingId") Long bookingId
    );


    @Update("""
            UPDATE folio
            SET total_amount = #{totalAmount},
                paid_amount = #{paidAmount},
                refunded_amount = #{refundedAmount},
                balance_amount = #{balanceAmount},
                settled_time = #{settledTime},
                update_time = NOW()
            WHERE id = #{folioId}
            """)
    int updateFinancialSummary(
            @Param("folioId") Long folioId,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("refundedAmount") BigDecimal refundedAmount,
            @Param("balanceAmount") BigDecimal balanceAmount,
            @Param("status") Integer status,
            @Param("settledTime") java.time.LocalDateTime settledTime
    );

    @Select("""
        SELECT *
        FROM folio
        WHERE id = #{folioId}
        FOR UPDATE
        """)
    Folio selectByIdForUpdate(
            @Param("folioId") Long folioId
    );

    @Update("UPDATE folio SET closed_time=#{time} WHERE id=#{id} AND closed_time IS NULL AND status=1 AND balance_amount=0")
    int close(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);

}
