package com.johnny.hotel.wallet;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface WalletMapper {
    @Insert("INSERT INTO wallet(user_id,currency) VALUES(#{userId},'CNY')")
    int open(Long userId);
    @Select("SELECT * FROM wallet WHERE id=#{id}")
    Wallet find(Long id);
    @Select("SELECT * FROM wallet WHERE user_id=#{userId}")
    Wallet byUser(Long userId);
    @Select("SELECT * FROM wallet WHERE id=#{id} FOR UPDATE")
    Wallet lock(Long id);
    @Select("SELECT * FROM wallet_transaction WHERE source_type=#{type} AND source_id=#{id} FOR UPDATE")
    WalletTransaction bySource(@Param("type") String type,@Param("id") Long id);
    @Update("UPDATE wallet SET status=#{status} WHERE id=#{id}")
    int status(@Param("id") Long id,@Param("status") int status);
    @Select("SELECT * FROM wallet_transaction WHERE wallet_id=#{id} AND id>#{after} ORDER BY id LIMIT 100")
    List<WalletTransaction> transactions(@Param("id") Long id,@Param("after") long after);
}
