package com.johnny.hotel;

import com.johnny.hotel.payment.*;import com.johnny.hotel.finance.FinanceOperationsService;
import com.johnny.hotel.settlement.*;
import com.johnny.hotel.settlement.SettlementRequests.*;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=HotelBackendApplication.class,properties={"hotel.wallet.dev.fixture=true","debug=false","logging.level.org.springframework=WARN","logging.level.com.johnny=WARN"})
@EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class SettlementDevelopmentTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){var e=WalletDevelopmentGuard.settings();r.add("spring.datasource.url",WalletDevelopmentGuard::url);r.add("spring.datasource.username",()->e.get("DB_USERNAME"));r.add("spring.datasource.password",()->e.get("DB_PASSWORD"));r.add("spring.data.redis.password",()->e.get("REDIS_PASSWORD"));}
 @Autowired SettlementService service; @Autowired PaymentCoreMapper transactions; @Autowired FinanceOperationsService finance; @Autowired JdbcTemplate jdbc;
 long actor; String run;
 @BeforeEach void setup(){actor=jdbc.queryForObject("SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.status=1 AND r.role_code IN ('OWNER','SUPER_ADMIN','FINANCE') ORDER BY FIELD(r.role_code,'OWNER','SUPER_ADMIN','FINANCE'),u.id LIMIT 1",Long.class);run="SET"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);authenticate();assertEquals("OPEN",jdbc.queryForObject("SELECT state FROM hotel_business_date_control WHERE id=1",String.class));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void authenticate(){var a=new UsernamePasswordAuthenticationToken("settlement-test",null,List.of());a.setDetails(actor);SecurityContextHolder.getContext().setAuthentication(a);}
 Import request(long source,String batch,BigDecimal gross){return Import.builder().provider("MOCK").providerBatchId(batch).currency("CNY").grossAmount(gross).feeAmount(new BigDecimal("2.00")).netAmount(gross.subtract(new BigDecimal("2.00"))).items(List.of(Item.builder().sourceType("SETTLEMENT_TEST").sourceId(source).providerPaymentId(run+"PAY").direction("IN").grossAmount(gross).feeAmount(new BigDecimal("2.00")).netAmount(gross.subtract(new BigDecimal("2.00"))).currency("CNY").build())).build();}
 void source(long id,BigDecimal amount){var date=jdbc.queryForObject("SELECT business_date FROM hotel_business_date_control WHERE id=1",LocalDate.class);assertEquals(1,transactions.insertTransaction(HotelTransactionRecord.builder().direction("EXTERNAL_IN").amount(amount).currency("CNY").sourceType("SETTLEMENT_TEST").sourceId(id).paymentMethod("CARD").channel("TEST").provider("MOCK").providerTransactionId(run+"PAY").initiatorType("SYSTEM").operatorUserId(actor).description("Settlement integration fixture").occurredAt(LocalDateTime.now()).businessDate(date).build()));}
 void inThread(Runnable action,CountDownLatch start,List<Throwable> errors){try{start.await();authenticate();action.run();}catch(Throwable e){synchronized(errors){errors.add(e);}}finally{SecurityContextHolder.clearContext();}}

 @Test void missingSourceRereconcilesAndConcurrentPayoutPostsExactlyOneInternalTransfer()throws Exception{long sourceId=Math.abs(UUID.randomUUID().getMostSignificantBits()>>>20);var imported=service.importBatch(request(sourceId,run+"B",new BigDecimal("100.00")));long batch=imported.getBatch().getId();assertEquals("MISSING",imported.getBatch().getReconciliationStatus());assertTrue(finance.exceptions(1,100).getItems().stream().anyMatch(x->"GATEWAY_SETTLEMENT_BATCH".equals(x.getSourceType())&&batch==x.getSourceId()));var payout=Payout.builder().providerPayoutId(run+"PO").amount(new BigDecimal("98.00")).currency("CNY").paidAt(LocalDateTime.now()).build();assertThrows(RuntimeException.class,()->service.payout(batch,payout));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM gateway_payout WHERE batch_id=?",Integer.class,batch));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_transaction_record WHERE source_type='PROCESSOR_PAYOUT' AND business_reference_id=?",Integer.class,batch));source(sourceId,new BigDecimal("100.00"));
  var errors=Collections.synchronizedList(new ArrayList<Throwable>());var reconcileGate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);for(int i=0;i<2;i++)pool.submit(()->inThread(()->service.reconcile(batch,Reconcile.builder().requestKey(run+"R").build()),reconcileGate,errors));reconcileGate.countDown();pool.shutdown();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));assertTrue(errors.isEmpty(),errors.toString());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM gateway_settlement_reconciliation_attempt WHERE batch_id=? AND request_key=?",Integer.class,batch,run+"R"));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM gateway_settlement_reconciliation_attempt WHERE batch_id=?",Integer.class,batch));assertEquals("MISSING",jdbc.queryForObject("SELECT status FROM gateway_settlement_item WHERE batch_id=?",String.class,batch));assertEquals("MATCHED",jdbc.queryForObject("SELECT reconciliation_status FROM gateway_settlement_batch WHERE id=?",String.class,batch));assertFalse(finance.exceptions(1,100).getItems().stream().anyMatch(x->"GATEWAY_SETTLEMENT_BATCH".equals(x.getSourceType())&&batch==x.getSourceId()));
  int revenueBefore=jdbc.queryForObject("SELECT COUNT(*) FROM revenue_recognition",Integer.class);errors.clear();var payoutGate=new CountDownLatch(1);pool=Executors.newFixedThreadPool(2);for(int i=0;i<2;i++)pool.submit(()->inThread(()->service.payout(batch,payout),payoutGate,errors));payoutGate.countDown();pool.shutdown();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));assertTrue(errors.isEmpty(),errors.toString());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM gateway_payout WHERE batch_id=?",Integer.class,batch));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_transaction_record WHERE source_type='PROCESSOR_PAYOUT' AND business_reference_id=?",Integer.class,batch));assertEquals("INTERNAL_TRANSFER",jdbc.queryForObject("SELECT direction FROM hotel_transaction_record WHERE source_type='PROCESSOR_PAYOUT' AND business_reference_id=?",String.class,batch));assertEquals(revenueBefore,jdbc.queryForObject("SELECT COUNT(*) FROM revenue_recognition",Integer.class));}

 @Test void wrongImmutableProviderAmountCannotBeReconciledOrPaid(){long sourceId=Math.abs(UUID.randomUUID().getLeastSignificantBits()>>>20);source(sourceId,new BigDecimal("99.00"));var imported=service.importBatch(request(sourceId,run+"B",new BigDecimal("100.00")));long batch=imported.getBatch().getId();assertEquals("MISMATCH",imported.getBatch().getReconciliationStatus());var retried=service.reconcile(batch,Reconcile.builder().requestKey(run+"R").build());assertEquals("MISMATCH",retried.getBatch().getReconciliationStatus());assertThrows(RuntimeException.class,()->service.payout(batch,Payout.builder().providerPayoutId(run+"PO").amount(new BigDecimal("98.00")).currency("CNY").paidAt(LocalDateTime.now()).build()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM gateway_payout WHERE batch_id=?",Integer.class,batch));}
}
