package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.Payment;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PaymentMapper {

    @Insert("""
        INSERT INTO payment
        (
            folio_id,
            amount,
            payment_method,
            status,
            reference_no,
            request_key,
            note,
            created_by,
            paid_time
        )
        VALUES
        (
            #{folioId},
            #{amount},
            #{paymentMethod},
            #{status},
            #{referenceNo},
            #{requestKey},
            #{note},
            #{createdBy},
            #{paidTime}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Payment payment);


    @Select("""
            SELECT *
            FROM payment
            WHERE id = #{id}
            """)
    Payment selectById(
            @Param("id") Long id
    );


    @Select("""
            SELECT *
            FROM payment
            WHERE folio_id = #{folioId}
            ORDER BY create_time ASC, id ASC
            """)
    List<Payment> selectByFolioId(
            @Param("folioId") Long folioId
    );


    @Select("""
            SELECT COALESCE(SUM(amount), 0.00)
            FROM payment
            WHERE folio_id = #{folioId}
              AND status = 'SUCCESS'
            """)
    BigDecimal sumSuccessfulAmountByFolioId(
            @Param("folioId") Long folioId
    );



    @Select("""
        SELECT *
        FROM payment
        WHERE folio_id = #{folioId}
          AND request_key = #{requestKey}
        FOR UPDATE
        """)
    Payment selectByFolioIdAndRequestKey(
            @Param("folioId") Long folioId,
            @Param("requestKey") String requestKey
    );

    @Select("SELECT * FROM payment WHERE folio_id=#{folioId} ORDER BY id FOR UPDATE")
    List<Payment> selectByFolioIdForUpdate(@Param("folioId") Long folioId);

}
