package com.johnny.hotel;

import com.johnny.hotel.payment.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import java.nio.charset.StandardCharsets;import java.math.BigDecimal;import java.time.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes=HotelBackendApplication.class,properties={"spring.flyway.baseline-on-migrate=false","hotel.payments.mock.webhook-secret=fresh-mock-secret-123456789","hotel.payment.recovery.pending-query-after=0s","hotel.payment.recovery.initial-backoff=10s","hotel.payment.recovery.max-backoff=40s","hotel.payment.recovery.fixed-delay=1h","spring.cache.type=none","logging.level.root=WARN"})
@AutoConfigureMockMvc
@TestPropertySource(properties="hotel.pagination.max-page-size=100")
@EnabledIfSystemProperty(named="hotel.payment.fresh.tests",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class PaymentCoreFreshMysqlTest {
 @Autowired JdbcTemplate jdbc;@Autowired ReservationCheckoutService checkouts;@Autowired PaymentAttemptService attempts;
 @Autowired PaymentWebhookService webhooks;@Autowired PaymentProviderRegistry providers;@Autowired HotelTransactionQueryService transactionQuery;@Autowired MockMvc mvc;@MockitoSpyBean ReservationPaymentFulfillmentService fulfillment;
 @Autowired PaymentRecoveryService recovery;@Autowired PaymentRecoveryClaimService claims;@Autowired MockPaymentProvider mockProvider;
 long customer,roomType;LocalDate arrival;
 @BeforeAll void seed(){
  jdbc.update("INSERT INTO sys_user(username,password,real_name,email,status) VALUES('payment_customer','test','Payment Customer','payment-customer@example.test',1)");customer=jdbc.queryForObject("SELECT id FROM sys_user WHERE username='payment_customer'",Long.class);
  jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='CUSTOMER'",customer);
  jdbc.update("INSERT INTO room_type(type_name,base_price,capacity,status) VALUES('payment_test_type',100.00,4,1)");roomType=jdbc.queryForObject("SELECT id FROM room_type WHERE type_name='payment_test_type'",Long.class);
  arrival=LocalDate.now().plusDays(30);
  jdbc.update("INSERT INTO reservation_policy(version_no,name,status,created_by,activated_by,activated_time) VALUES(9001,'Payment policy A',1,?,?,NOW(6))",customer,customer);
  long policy=jdbc.queryForObject("SELECT id FROM reservation_policy WHERE version_no=9001",Long.class);jdbc.update("INSERT INTO reservation_policy_band(policy_id,min_lead_days,max_lead_days,refund_percent) VALUES(?,0,NULL,75.00)",policy);
 }
 PaymentRequests.Checkout checkoutRequest(String key){var r=new PaymentRequests.Checkout();r.setRoomTypeId(roomType);r.setGuestCount(2);r.setCheckInDate(arrival);r.setCheckOutDate(arrival.plusDays(2));r.setRequestKey(key);return r;}
 PaymentRequests.Attempt attemptRequest(String key){var r=new PaymentRequests.Attempt();r.setProvider("MOCK");r.setRequestKey(key);return r;}
 record Event(String eventId,String merchant,String providerId,String status,BigDecimal amount,String currency){}
 Event event(PaymentAttempt a,String status,BigDecimal amount,String currency){return new Event("evt_"+UUID.randomUUID(),a.getMerchantPaymentNo(),"provider_"+a.getId(),status,amount,currency);}
 String sign(Event e)throws Exception{String value=String.join("|",e.eventId(),"payment.updated",e.merchant(),e.providerId(),e.status(),e.amount().toPlainString(),e.currency());Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec("fresh-mock-secret-123456789".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
 RawPaymentWebhook raw(Event e,String signature){String body="{\"providerEventId\":\""+e.eventId()+"\",\"eventType\":\"payment.updated\",\"merchantPaymentNo\":\""+e.merchant()+"\",\"providerPaymentId\":\""+e.providerId()+"\",\"status\":\""+e.status()+"\",\"amount\":"+e.amount().toPlainString()+",\"currency\":\""+e.currency()+"\"}";return new RawPaymentWebhook(Map.of("X-Payment-Signature",List.of(signature)),body.getBytes(StandardCharsets.UTF_8));}
 ReservationCheckoutSession checkout(String key){return checkouts.create(checkoutRequest(key),customer);}PaymentAttempt attempt(ReservationCheckoutSession c,String key){return attempts.create(c.getId(),attemptRequest(key),customer);}
 void recoverOne(PaymentAttempt attempt){String token=claims.claim(attempt.getId());assertNotNull(token);recovery.recover(attempt.getId(),token);}
 @Test void fullPaymentCoreContract()throws Exception{
  String checkoutKey="co_"+UUID.randomUUID().toString().replace("-","").substring(0,16),attemptKey="pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
  var pool=Executors.newFixedThreadPool(2);try{var calls=List.of(pool.submit(()->checkout(checkoutKey)),pool.submit(()->checkout(checkoutKey)));var c1=calls.get(0).get(10,TimeUnit.SECONDS);var c2=calls.get(1).get(10,TimeUnit.SECONDS);assertEquals(c1.getId(),c2.getId());}finally{pool.shutdownNow();}
  var checkout=checkouts.get(jdbc.queryForObject("SELECT id FROM reservation_checkout_session WHERE request_key=?",Long.class,checkoutKey),customer);BigDecimal frozen=checkout.getQuotedTotal();long frozenPolicy=checkout.getReservationPolicyId();
  pool=Executors.newFixedThreadPool(2);try{var calls=List.of(pool.submit(()->attempt(checkout,attemptKey)),pool.submit(()->attempt(checkout,attemptKey)));assertEquals(calls.get(0).get(10,TimeUnit.SECONDS).getId(),calls.get(1).get(10,TimeUnit.SECONDS).getId());}finally{pool.shutdownNow();}
  var attempt=jdbc.queryForObject("SELECT id FROM payment_attempt WHERE checkout_session_id=?",Long.class,checkout.getId());var payment=attempts.get(attempt,customer);
  var invalid=event(payment,"SUCCEEDED",payment.getAmount(),payment.getCurrency());assertThrows(RuntimeException.class,()->webhooks.receive("MOCK",raw(invalid,"invalid")));
  var wrongAmount=event(payment,"SUCCEEDED",payment.getAmount().add(BigDecimal.ONE),payment.getCurrency());assertThrows(RuntimeException.class,()->webhooks.receive("MOCK",raw(wrongAmount,sign(wrongAmount))));
  var wrongCurrency=event(payment,"SUCCEEDED",payment.getAmount(),"USD");assertThrows(RuntimeException.class,()->webhooks.receive("MOCK",raw(wrongCurrency,sign(wrongCurrency))));
  assertEquals("PENDING",attempts.get(attempt,customer).getStatus());
  jdbc.update("UPDATE room_type SET base_price=999.00 WHERE id=?",roomType);jdbc.update("UPDATE reservation_policy SET status=2 WHERE id=?",frozenPolicy);
  jdbc.update("INSERT INTO reservation_policy(version_no,name,status,created_by,activated_by,activated_time) VALUES(9002,'Payment policy B',1,?,?,NOW(6))",customer,customer);
  var success=event(payment,"SUCCEEDED",payment.getAmount(),payment.getCurrency());RawPaymentWebhook successRaw=raw(success,sign(success));
  pool=Executors.newFixedThreadPool(2);try{var calls=List.of(pool.submit(()->webhooks.receive("MOCK",successRaw)),pool.submit(()->webhooks.receive("MOCK",successRaw)));calls.get(0).get(10,TimeUnit.SECONDS);calls.get(1).get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  var done=attempts.get(attempt,customer);assertEquals("SUCCEEDED",done.getStatus());assertEquals("COMPLETED",done.getFulfillmentStatus());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,checkoutKey));long booking=done.getBookingId();
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_payment p JOIN reservation_deposit_account a ON a.id=p.account_id WHERE a.booking_id=?",Integer.class,booking));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_transaction_record WHERE source_type='DEPOSIT_PAYMENT' AND business_reference_id=?",Integer.class,booking));
  assertEquals(0,frozen.compareTo(jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,booking)));assertEquals(frozenPolicy,jdbc.queryForObject("SELECT reservation_policy_id FROM booking WHERE id=?",Long.class,booking));
  assertEquals(0,frozen.compareTo(jdbc.queryForObject("SELECT SUM(rate_amount) FROM booking_nightly_rate WHERE booking_id=?",BigDecimal.class,booking)));
  var register=transactionQuery.query(1,20,null,null,"EXTERNAL_IN","DEPOSIT_PAYMENT","MOCK","MOCK","CNY","BOOKING",booking,success.providerId());assertEquals(1,register.getItems().size());assertEquals(booking,register.getItems().get(0).getBusinessReferenceId());
  var staleFailure=event(payment,"FAILED",payment.getAmount(),payment.getCurrency());webhooks.receive("MOCK",raw(staleFailure,sign(staleFailure)));assertEquals("SUCCEEDED",attempts.get(attempt,customer).getStatus());
  for(String role:List.of("FINANCE","OWNER","SUPER_ADMIN"))mvc.perform(get("/api/finance/transactions").with(SecurityMockMvcRequestPostProcessors.authentication(auth(role)))).andExpect(status().isOk());
  for(String role:List.of("CUSTOMER","STAFF","HR_ADMIN","MANAGER"))mvc.perform(get("/api/finance/transactions").with(SecurityMockMvcRequestPostProcessors.authentication(auth(role)))).andExpect(status().isForbidden());
 }
 @Test void mockProviderContractConsumesRawProtocolAndNormalizes()throws Exception{
  var event=new Event("evt_contract_"+UUID.randomUUID(),"merchant_contract","provider_contract","SUCCEEDED",new BigDecimal("25.00"),"CNY");
  var normalized=providers.require("MOCK").verifyAndNormalize(raw(event,sign(event)));
  assertEquals("MOCK",normalized.provider());assertEquals(event.eventId(),normalized.providerEventId());assertEquals(event.merchant(),normalized.merchantPaymentNo());assertEquals(event.providerId(),normalized.providerPaymentId());assertEquals(0,event.amount().compareTo(normalized.amount()));assertNotNull(normalized.occurredAt());
  assertThrows(RuntimeException.class,()->providers.require("MOCK").verifyAndNormalize(raw(event,"bad")));
 }
 @Test void fulfillmentFailureIsRecoverableAndRetryIsExactlyOnce()throws Exception{
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var success=event(a,"SUCCEEDED",a.getAmount(),a.getCurrency());var raw=raw(success,sign(success));
  doThrow(new RuntimeException("injected fulfillment failure")).doCallRealMethod().when(fulfillment).fulfill(a.getId());webhooks.receive("MOCK",raw);
  assertEquals("SUCCEEDED",attempts.get(a.getId(),customer).getStatus());assertEquals("FAILED_RETRYABLE",attempts.get(a.getId(),customer).getFulfillmentStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c.getRequestKey()));
  recoverOne(a);var done=attempts.get(a.getId(),customer);assertEquals("COMPLETED",done.getFulfillmentStatus());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c.getRequestKey()));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_payment WHERE reference_no=?",Integer.class,String.valueOf(a.getId())));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_transaction_record WHERE business_reference_id=?",Integer.class,done.getBookingId()));
 }
 @Test void missingWebhookQuerySuccessAndTerminalFailureUseSharedStateMachine(){
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));
  mockProvider.setQueryResult(a.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a.getMerchantPaymentNo(),"query_"+a.getId(),"SUCCEEDED",a.getAmount(),a.getCurrency(),LocalDateTime.now()));
  recoverOne(a);var done=attempts.get(a.getId(),customer);assertEquals("SUCCEEDED",done.getStatus());assertEquals("COMPLETED",done.getFulfillmentStatus());
  var c2=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a2=attempt(c2,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));mockProvider.setQueryResult(a2.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a2.getMerchantPaymentNo(),"query_"+a2.getId(),"FAILED",a2.getAmount(),a2.getCurrency(),LocalDateTime.now()));recoverOne(a2);assertEquals("FAILED",attempts.get(a2.getId(),customer).getStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c2.getRequestKey()));
 }
 @Test void pendingTemporaryFailureBackoffAndMismatchManualReview(){
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));
  mockProvider.setQueryFailure(a.getMerchantPaymentNo(),new RuntimeException("temporary timeout token=redacted"));recoverOne(a);var retry=attempts.get(a.getId(),customer);assertEquals("PENDING",retry.getStatus());assertEquals(1,retry.getRecoveryRetryCount());assertNotNull(retry.getNextRecoveryAt());
  jdbc.update("UPDATE payment_attempt SET next_recovery_at=DATE_SUB(NOW(6),INTERVAL 1 SECOND) WHERE id=?",a.getId());mockProvider.setQueryResult(a.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a.getMerchantPaymentNo(),"query_"+a.getId(),"SUCCEEDED",a.getAmount().add(BigDecimal.ONE),a.getCurrency(),LocalDateTime.now()));recoverOne(a);retry=attempts.get(a.getId(),customer);assertEquals("MANUAL_REVIEW",retry.getRecoveryStatus());assertEquals("PENDING",retry.getStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c.getRequestKey()));
 }
 @Test void claimIsSingleOwnerAndExpiredLeaseCanBeReclaimed()throws Exception{
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));
  var pool=Executors.newFixedThreadPool(2);try{var x=pool.submit(()->claims.claim(a.getId()));var y=pool.submit(()->claims.claim(a.getId()));String first=x.get(10,TimeUnit.SECONDS),second=y.get(10,TimeUnit.SECONDS);assertTrue((first==null)^(second==null));}finally{pool.shutdownNow();}
  jdbc.update("UPDATE payment_attempt SET recovery_claim_until=DATE_SUB(NOW(6),INTERVAL 1 SECOND) WHERE id=?",a.getId());assertNotNull(claims.claim(a.getId()));
 }
 @Test void processingBackoffExpiredCheckoutAndCompletedExclusion(){
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));
  jdbc.update("UPDATE reservation_checkout_session SET expires_at=DATE_SUB(NOW(6),INTERVAL 1 DAY) WHERE id=?",c.getId());
  mockProvider.setQueryResult(a.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a.getMerchantPaymentNo(),"query_"+a.getId(),"PROCESSING",a.getAmount(),a.getCurrency(),LocalDateTime.now()));recoverOne(a);var processing=attempts.get(a.getId(),customer);assertEquals("PROCESSING",processing.getStatus());assertNotNull(processing.getNextRecoveryAt());
  jdbc.update("UPDATE payment_attempt SET next_recovery_at=DATE_SUB(NOW(6),INTERVAL 1 SECOND) WHERE id=?",a.getId());mockProvider.setQueryResult(a.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a.getMerchantPaymentNo(),"query_"+a.getId(),"SUCCEEDED",a.getAmount(),a.getCurrency(),LocalDateTime.now()));recoverOne(a);assertEquals("COMPLETED",attempts.get(a.getId(),customer).getFulfillmentStatus());assertFalse(claims.candidates().contains(a.getId()));
 }
 @Test void queryCurrencyAndProviderIdMismatchNeverFulfill(){
  for(boolean currencyMismatch:List.of(true,false)){
   var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));
   if(!currencyMismatch)jdbc.update("UPDATE payment_attempt SET provider_payment_id=? WHERE id=?","expected_"+a.getId(),a.getId());
   mockProvider.setQueryResult(a.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a.getMerchantPaymentNo(),currencyMismatch?"query_"+a.getId():"conflict_"+a.getId(),"SUCCEEDED",a.getAmount(),currencyMismatch?"USD":a.getCurrency(),LocalDateTime.now()));recoverOne(a);assertEquals("MANUAL_REVIEW",attempts.get(a.getId(),customer).getRecoveryStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c.getRequestKey()));
  }
 }
 @Test void webhookAndQuerySuccessRaceCreatesOneFinancialFact()throws Exception{
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));String providerId="race_"+a.getId();var e=new Event("evt_"+UUID.randomUUID(),a.getMerchantPaymentNo(),providerId,"SUCCEEDED",a.getAmount(),a.getCurrency());mockProvider.setQueryResult(a.getMerchantPaymentNo(),new ProviderPaymentResult("MOCK",a.getMerchantPaymentNo(),providerId,"SUCCEEDED",a.getAmount(),a.getCurrency(),LocalDateTime.now()));
  RawPaymentWebhook webhook=raw(e,sign(e));var pool=Executors.newFixedThreadPool(2);try{var q=pool.submit(()->recovery.runOneCycle());var w=pool.submit(()->webhooks.receive("MOCK",webhook));q.get(10,TimeUnit.SECONDS);w.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  var done=attempts.get(a.getId(),customer);assertEquals("COMPLETED",done.getFulfillmentStatus());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,c.getRequestKey()));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_payment WHERE reference_no=?",Integer.class,String.valueOf(a.getId())));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_transaction_record WHERE provider_transaction_id=?",Integer.class,providerId));
 }
 @Test void disabledRecoveryDoesNothing(){
  var c=checkout("co_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var a=attempt(c,"pa_"+UUID.randomUUID().toString().replace("-","").substring(0,16));var properties=(PaymentRecoveryProperties)org.springframework.test.util.ReflectionTestUtils.getField(recovery,"config");boolean old=properties.isEnabled();try{properties.setEnabled(false);assertEquals(0,recovery.runOneCycle());assertEquals("PENDING",attempts.get(a.getId(),customer).getStatus());}finally{properties.setEnabled(old);}
 }
 UsernamePasswordAuthenticationToken auth(String role){var a=new UsernamePasswordAuthenticationToken("test","n/a",List.of(new SimpleGrantedAuthority("ROLE_"+role)));a.setDetails(customer);return a;}
}
