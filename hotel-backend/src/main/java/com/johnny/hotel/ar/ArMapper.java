package com.johnny.hotel.ar;
import org.apache.ibatis.annotations.*;import java.math.*;import java.util.*;
@Mapper public interface ArMapper {
 @Insert("INSERT INTO ar_request_lock(request_key) VALUES(#{key}) ON DUPLICATE KEY UPDATE request_key=VALUES(request_key)")int ensureRequest(@Param("key")String key);
 @Select("SELECT request_key FROM ar_request_lock WHERE request_key=#{key} FOR UPDATE")String lockRequest(@Param("key")String key);
 @Insert("INSERT INTO ar_account(account_type,name,status,currency,credit_limit,payment_terms_days,contact_name,contact_email,external_reference,created_by) VALUES(#{accountType},#{name},'ACTIVE',#{currency},#{creditLimit},#{paymentTermsDays},#{contactName},#{contactEmail},#{externalReference},#{createdBy})")@Options(useGeneratedKeys=true,keyProperty="id")int insertAccount(ArAccount a);
 @Select("SELECT * FROM ar_account WHERE id=#{id}")ArAccount account(Long id);@Select("SELECT * FROM ar_account WHERE id=#{id} FOR UPDATE")ArAccount lockAccount(Long id);
 @Update("UPDATE ar_account SET status=#{status} WHERE id=#{id} AND status<>#{status}")int status(@Param("id")Long id,@Param("status")String status);
 @Select("SELECT COALESCE(SUM(CASE WHEN entry_type='FOLIO_TRANSFER' THEN amount WHEN entry_type IN('PAYMENT','CREDIT','REVERSAL') THEN -amount ELSE 0 END),0) FROM ar_ledger_entry WHERE account_id=#{id}")BigDecimal outstanding(Long id);
 @Select("SELECT COALESCE(SUM(amount),0) FROM ar_ledger_entry WHERE folio_id=#{id} AND entry_type='FOLIO_TRANSFER'")BigDecimal folioTransfers(Long id);
 @Select("SELECT * FROM ar_ledger_entry WHERE request_key=#{key}")ArLedgerEntry byRequest(String key);
 @Insert("INSERT INTO ar_ledger_entry(account_id,entry_type,amount,currency,folio_id,source_type,source_id,request_key,reason,payment_method,external_reference,operator_user_id,posting_business_date,occurred_at) VALUES(#{accountId},#{entryType},#{amount},#{currency},#{folioId},#{sourceType},#{sourceId},#{requestKey},#{reason},#{paymentMethod},#{externalReference},#{operatorUserId},#{postingBusinessDate},#{occurredAt})")@Options(useGeneratedKeys=true,keyProperty="id")int insert(ArLedgerEntry e);
 @Select("SELECT * FROM ar_ledger_entry WHERE account_id=#{id} ORDER BY posting_business_date DESC,id DESC LIMIT #{offset},#{size}")List<ArLedgerEntry> entries(@Param("id")Long id,@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT COUNT(*) FROM ar_ledger_entry WHERE account_id=#{id}")long count(Long id);
 @Select("SELECT a.*,COALESCE(SUM(CASE WHEN l.entry_type='FOLIO_TRANSFER' THEN l.amount WHEN l.entry_type IN('PAYMENT','CREDIT','REVERSAL') THEN -l.amount ELSE 0 END),0) outstanding FROM ar_account a LEFT JOIN ar_ledger_entry l ON l.account_id=a.id GROUP BY a.id ORDER BY a.name,id LIMIT #{offset},#{size}")List<java.util.Map<String,Object>> accountBalances(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT COUNT(*) FROM ar_account")long accountCount();
 @Select("SELECT COALESCE(SUM(CASE WHEN entry_type='FOLIO_TRANSFER' THEN amount WHEN entry_type IN('PAYMENT','CREDIT','REVERSAL') THEN -amount ELSE 0 END),0) FROM ar_ledger_entry")BigDecimal totalOutstanding();
}
