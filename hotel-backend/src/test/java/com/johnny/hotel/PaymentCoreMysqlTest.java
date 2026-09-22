package com.johnny.hotel;

import com.johnny.hotel.payment.*;
import com.johnny.hotel.support.IsolatedMysqlTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import java.nio.charset.StandardCharsets;import java.math.BigDecimal;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="hotel.payments.mock.webhook-secret=isolated-mock-secret-123456789")
class PaymentCoreMysqlTest extends IsolatedMysqlTest {
 @Autowired ReservationCheckoutService checkouts;@Autowired PaymentAttemptService attempts;@Autowired PaymentWebhookService webhooks;
 private PaymentRequests.Checkout checkout(String key){var r=new PaymentRequests.Checkout();r.setRoomTypeId(1L);r.setGuestCount(2);r.setCheckInDate(arrival);r.setCheckOutDate(arrival.plusDays(2));r.setRequestKey(key);return r;}
 private PaymentAttempt attempt(ReservationCheckoutSession c,String key){var r=new PaymentRequests.Attempt();r.setProvider("MOCK");r.setRequestKey(key);return attempts.create(c.getId(),r,1L);}
 record Event(String providerEventId,String eventType,String merchantPaymentNo,String providerPaymentId,String status,BigDecimal amount,String currency){}
 private Event event(PaymentAttempt a,String id,String status,BigDecimal amount,String currency){return new Event(id,"payment.updated",a.getMerchantPaymentNo(),"mock-provider-"+a.getId(),status,amount,currency);}
 private String sign(Event e)throws Exception{String s=String.join("|",e.providerEventId(),e.eventType(),e.merchantPaymentNo(),e.providerPaymentId(),e.status(),e.amount().toPlainString(),e.currency());Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec("isolated-mock-secret-123456789".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(s.getBytes(StandardCharsets.UTF_8)));}
 private RawPaymentWebhook raw(Event e,String signature){String body="{\"providerEventId\":\""+e.providerEventId()+"\",\"eventType\":\""+e.eventType()+"\",\"merchantPaymentNo\":\""+e.merchantPaymentNo()+"\",\"providerPaymentId\":\""+e.providerPaymentId()+"\",\"status\":\""+e.status()+"\",\"amount\":"+e.amount().toPlainString()+",\"currency\":\""+e.currency()+"\"}";return new RawPaymentWebhook(Map.of("X-Payment-Signature",List.of(signature)),body.getBytes(StandardCharsets.UTF_8));}
 @Test void duplicateCheckoutAttemptAndWebhookFulfillExactlyOnce()throws Exception{
  String ck="checkout_"+UUID.randomUUID().toString().replace("-","").substring(0,12),ak="attempt_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  var c=checkouts.create(checkout(ck),1L);assertEquals(c.getId(),checkouts.create(checkout(ck),1L).getId());
  var a=attempt(c,ak);assertEquals(a.getId(),attempt(c,ak).getId());var e=event(a,"evt_"+UUID.randomUUID(),"SUCCEEDED",a.getAmount(),a.getCurrency());
  webhooks.receive("MOCK",raw(e,sign(e)));webhooks.receive("MOCK",raw(e,sign(e)));
  assertEquals("COMPLETED",attempts.get(a.getId(),1L).getFulfillmentStatus());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,ck));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_payment WHERE reference_no=?",Integer.class,String.valueOf(a.getId())));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_transaction_record WHERE provider_transaction_id=?",Integer.class,e.providerPaymentId()));
 }
 @Test void invalidSignatureAmountAndCurrencyNeverFulfill()throws Exception{
  var c=checkouts.create(checkout("checkout_"+UUID.randomUUID().toString().replace("-","").substring(0,12)),1L);var a=attempt(c,"attempt_"+UUID.randomUUID().toString().replace("-","").substring(0,12));
  var invalidSignature=event(a,"evt_"+UUID.randomUUID(),"SUCCEEDED",a.getAmount(),a.getCurrency());assertThrows(RuntimeException.class,()->webhooks.receive("MOCK",raw(invalidSignature,"bad")));
  var wrongAmount=event(a,"evt_"+UUID.randomUUID(),"SUCCEEDED",a.getAmount().add(BigDecimal.ONE),a.getCurrency());assertThrows(RuntimeException.class,()->webhooks.receive("MOCK",raw(wrongAmount,sign(wrongAmount))));
  var wrongCurrency=event(a,"evt_"+UUID.randomUUID(),"SUCCEEDED",a.getAmount(),"USD");assertThrows(RuntimeException.class,()->webhooks.receive("MOCK",raw(wrongCurrency,sign(wrongCurrency))));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c.getRequestKey()));
 }
 @Test void successWinsOverLaterFailure()throws Exception{
  var c=checkouts.create(checkout("checkout_"+UUID.randomUUID().toString().replace("-","").substring(0,12)),1L);var a=attempt(c,"attempt_"+UUID.randomUUID().toString().replace("-","").substring(0,12));
  var success=event(a,"evt_"+UUID.randomUUID(),"SUCCEEDED",a.getAmount(),a.getCurrency());webhooks.receive("MOCK",raw(success,sign(success)));
  var failure=event(a,"evt_"+UUID.randomUUID(),"FAILED",a.getAmount(),a.getCurrency());webhooks.receive("MOCK",raw(failure,sign(failure)));assertEquals("SUCCEEDED",attempts.get(a.getId(),1L).getStatus());
 }
}
