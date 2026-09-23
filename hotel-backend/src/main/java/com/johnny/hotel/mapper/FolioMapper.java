package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.Folio;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;

@Mapper
public interface FolioMapper {

    @Select("SELECT f.*,(SELECT s.booking_id FROM stay s WHERE s.id=f.stay_id) AS booking_id FROM folio f WHERE f.id=(SELECT id FROM folio WHERE stay_id=#{stayId}) FOR UPDATE")
    Folio selectByStayIdForUpdate(Long stayId);

    @Insert("""
            INSERT INTO folio
            (
                stay_id,
                currency,
                total_amount,
                paid_amount,
                balance_amount
            )
            VALUES
            (
                #{stayId},
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
            SELECT f.*,(SELECT s.booking_id FROM stay s WHERE s.id=f.stay_id) AS booking_id
            FROM folio f
            WHERE f.id = #{id}
            """)
    Folio selectById(
            @Param("id") Long id
    );


    @Select("""
            SELECT f.*,(SELECT s.booking_id FROM stay s WHERE s.id=f.stay_id) AS booking_id
            FROM folio f
            WHERE f.stay_id = (SELECT s.id FROM stay s WHERE s.booking_id=#{bookingId})
            """)
    Folio selectByBookingId(
            @Param("bookingId") Long bookingId
    );


    // Resolve immutable booking -> folio identity without a secondary-index lock, then lock PRIMARY.
    // Otherwise Payment(PRIMARY) -> summary(uk_folio_booking) can deadlock with checkout.
    @Select("""
            SELECT f.*,(SELECT s.booking_id FROM stay s WHERE s.id=f.stay_id) AS booking_id
            FROM folio f
            WHERE f.id = (SELECT x.id FROM folio x JOIN stay s ON s.id=x.stay_id WHERE s.booking_id = #{bookingId})
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
                ar_transferred_amount = #{arTransferredAmount},
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
            @Param("refundedAmount") BigDecimal refundedAmount,
            @Param("arTransferredAmount") BigDecimal arTransferredAmount,
            @Param("balanceAmount") BigDecimal balanceAmount,
            @Param("status") Integer status,
            @Param("settledTime") java.time.LocalDateTime settledTime
    );

    @Select("""
        SELECT f.*,(SELECT s.booking_id FROM stay s WHERE s.id=f.stay_id) AS booking_id
        FROM folio f
        WHERE f.id = #{folioId}
        FOR UPDATE
        """)
    Folio selectByIdForUpdate(
            @Param("folioId") Long folioId
    );

    @Update("UPDATE folio SET closed_time=#{time} WHERE id=#{id} AND closed_time IS NULL AND status=1 AND balance_amount=0")
    int close(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);

}
