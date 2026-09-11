package com.johnny.hotel.wallet;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface WalletTopUpMapper {
    @Select("SELECT * FROM wallet_top_up WHERE wallet_id=#{walletId} AND request_key=#{key} FOR UPDATE")
    WalletTopUp byKey(@Param("walletId") Long walletId,@Param("key") String key);
    @Select("SELECT * FROM wallet_top_up WHERE wallet_id=#{walletId} AND id=#{id} FOR UPDATE")
    WalletTopUp lock(@Param("walletId") Long walletId,@Param("id") Long id);
    @Select("SELECT * FROM wallet_top_up WHERE wallet_id=#{walletId} AND id>#{after} ORDER BY id LIMIT 100")
    List<WalletTopUp> list(@Param("walletId") Long walletId,@Param("after") long after);
    @Insert("INSERT INTO wallet_top_up(wallet_id,amount,request_key,requested_by) VALUES(#{walletId},#{amount},#{requestKey},#{requestedBy})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(WalletTopUp request);
    @Update("""
        UPDATE wallet_top_up SET status=#{status},resolution_key=#{key},resolved_by=#{actor},reason=#{reason},resolved_time=NOW(6)
        WHERE id=#{id} AND status=0
        """)
    int resolve(@Param("id") Long id,@Param("status") int status,@Param("key") String key,
                @Param("actor") Long actor,@Param("reason") String reason);
}
