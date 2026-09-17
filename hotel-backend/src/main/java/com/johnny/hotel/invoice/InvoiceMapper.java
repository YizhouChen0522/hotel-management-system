package com.johnny.hotel.invoice;import org.apache.ibatis.annotations.*;import java.util.*;
@Mapper public interface InvoiceMapper{
 @Insert("INSERT INTO invoice_request_lock(request_key) VALUES(#{key}) ON DUPLICATE KEY UPDATE request_key=VALUES(request_key)")int ensureRequest(String key);
 @Select("SELECT request_key FROM invoice_request_lock WHERE request_key=#{key} FOR UPDATE")String lockRequest(String key);
 @Insert("INSERT INTO invoice_number_sequence VALUES()")@Options(useGeneratedKeys=true,keyProperty="id")int sequence(InvoiceNumber n);
 @Insert("INSERT INTO invoice(invoice_number,request_key,folio_id,booking_id,currency,recipient_name,recipient_email,billing_address,tax_identifier,status,subtotal,total_amount,issued_by,issued_time) VALUES(#{invoiceNumber},#{requestKey},#{folioId},#{bookingId},#{currency},#{recipientName},#{recipientEmail},#{billingAddress},#{taxIdentifier},#{status},#{subtotal},#{totalAmount},#{issuedBy},#{issuedTime})")@Options(useGeneratedKeys=true,keyProperty="id")int insert(Invoice i);
 @Insert("INSERT INTO invoice_line(invoice_id,line_number,folio_item_id,item_type,description,business_date,quantity,unit_price,amount) VALUES(#{invoiceId},#{lineNumber},#{folioItemId},#{itemType},#{description},#{businessDate},#{quantity},#{unitPrice},#{amount})")int insertLine(InvoiceLine l);
 @Select("SELECT * FROM invoice WHERE id=#{id}")Invoice find(Long id);@Select("SELECT * FROM invoice WHERE id=#{id} FOR UPDATE")Invoice lock(Long id);
 @Select("SELECT * FROM invoice WHERE request_key=#{key} FOR UPDATE")Invoice byRequest(String key);
 @Select("SELECT * FROM invoice_line WHERE invoice_id=#{id} ORDER BY line_number")List<InvoiceLine> lines(Long id);
 @Select("SELECT * FROM invoice WHERE folio_id=#{folio} ORDER BY id DESC")List<Invoice> byFolio(Long folio);
 @Update("UPDATE invoice SET status=1,voided_by=#{actor},voided_time=CURRENT_TIMESTAMP(6),void_reason=#{reason} WHERE id=#{id} AND status=0")int voidInvoice(@Param("id")Long id,@Param("actor")Long actor,@Param("reason")String reason);
}
