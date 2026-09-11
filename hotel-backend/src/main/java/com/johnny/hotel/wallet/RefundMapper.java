package com.johnny.hotel.wallet;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface RefundMapper {
    @Select("SELECT * FROM refund WHERE folio_id=#{id} ORDER BY id FOR UPDATE")
    List<Refund> forFolio(Long id);
    @Insert("""
        INSERT INTO refund(folio_id,booking_id,user_id,wallet_id,currency,amount,request_key,reason,destination,requested_by)
        VALUES(#{folioId},#{bookingId},#{userId},#{walletId},#{currency},#{amount},#{requestKey},#{reason},'HOTEL_WALLET',#{requestedBy})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Refund refund);
    @Update("""
        UPDATE refund SET status=#{status},processed_by=#{actor},process_key=#{key},process_reason=#{reason},processed_time=NOW(6)
        WHERE id=#{id} AND status=0
        """)
    int process(@Param("id") Long id,@Param("status") int status,@Param("actor") Long actor,
                @Param("key") String key,@Param("reason") String reason);
}
