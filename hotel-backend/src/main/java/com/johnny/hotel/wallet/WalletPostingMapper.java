package com.johnny.hotel.wallet;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;

// Package-private: only the wallet posting boundary can mutate money. Never accepts a new balance.
@Mapper
interface WalletPostingMapper {
    @Update("UPDATE wallet SET balance=balance-#{amount} WHERE id=#{id} AND #{amount}>0 AND balance>=#{amount}")
    int debit(@Param("id") Long id,@Param("amount") BigDecimal amount);
    @Update("UPDATE wallet SET balance=balance+#{amount} WHERE id=#{id} AND #{amount}>0 AND balance<=9999999999.99-#{amount}")
    int credit(@Param("id") Long id,@Param("amount") BigDecimal amount);
    @Insert("""
        INSERT INTO wallet_transaction(wallet_id,transaction_type,amount,balance_before,balance_after,
            request_key,source_type,source_id,operator_user_id)
        VALUES(#{walletId},#{transactionType},#{amount},#{balanceBefore},#{balanceAfter},
            #{requestKey},#{sourceType},#{sourceId},#{operatorUserId})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int append(WalletTransaction transaction);
}
