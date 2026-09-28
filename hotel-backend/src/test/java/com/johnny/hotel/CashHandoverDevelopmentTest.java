package com.johnny.hotel;

import com.johnny.hotel.cashier.CashierModels.*;
import com.johnny.hotel.cashier.CashierService;
import com.johnny.hotel.finance.FinanceOperationsService;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=HotelBackendApplication.class,properties={"hotel.wallet.dev.fixture=true","spring.cache.type=none","logging.level.org.springframework=WARN"})
@EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class CashHandoverDevelopmentTest {
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){var e=WalletDevelopmentGuard.settings();r.add("spring.datasource.url",WalletDevelopmentGuard::url);r.add("spring.datasource.username",()->e.get("DB_USERNAME"));r.add("spring.datasource.password",()->e.get("DB_PASSWORD"));r.add("spring.data.redis.password",()->e.get("REDIS_PASSWORD"));}
 @Autowired CashierService service;@Autowired FinanceOperationsService finance;@Autowired JdbcTemplate jdbc;

 @Test void realMysqlHandoverIsConcurrentImmutableAndVisibleOnlyWhileUnresolved()throws Exception{
  long owner=user("OWNER","SUPER_ADMIN"),first=user("STAFF","MANAGER","OWNER","SUPER_ADMIN"),next=anotherOperational(first),manager=user("MANAGER","OWNER","SUPER_ADMIN");
  auth(owner);String code="HO-"+UUID.randomUUID().toString().substring(0,8);var drawer=service.createDrawer(new CreateDrawer(code,"Handover integration","CNY"));
  auth(first);var a=service.open(drawer.getId(),new Count(new BigDecimal("1630.00"),null));service.countClose(a.shift().getId(),new Count(new BigDecimal("1630.00"),null));service.close(a.shift().getId(),new Count(new BigDecimal("1630.00"),null));
  auth(next);var b=service.open(drawer.getId(),new Count(new BigDecimal("1620.00"),null));var handover=b.handover();assertNotNull(handover);assertEquals("SHORT",handover.getDifferenceType());assertEquals(new BigDecimal("1630.00"),handover.getPreviousClosingCount());assertEquals(new BigDecimal("1620.00"),handover.getNextOpeningCount());
  long movements=jdbc.queryForObject("SELECT COUNT(*) FROM cash_drawer_movement WHERE drawer_id=?",Long.class,drawer.getId()),expenses=jdbc.queryForObject("SELECT COUNT(*) FROM hotel_expense",Long.class);

  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{Callable<String> call=()->{auth(next);start.await();return service.confirmHandover(handover.getId()).getStatus();};var x=pool.submit(call);var y=pool.submit(call);start.countDown();assertEquals("REVIEW_REQUIRED",x.get(10,TimeUnit.SECONDS));assertEquals("REVIEW_REQUIRED",y.get(10,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE action='CONFIRM_CASH_HANDOVER' AND operator_id=? AND detail=?",Integer.class,next,"Cashier "+handover.getId()));
  assertTrue(finance.exceptions(1,100).getItems().stream().anyMatch(e->"CASH_HANDOVER".equals(e.getSourceType())&&handover.getId().equals(e.getSourceId())));
  assertThrows(Exception.class,()->jdbc.update("UPDATE cash_handover SET next_opening_count=1630.00 WHERE id=?",handover.getId()));
  assertEquals(movements,jdbc.queryForObject("SELECT COUNT(*) FROM cash_drawer_movement WHERE drawer_id=?",Long.class,drawer.getId()));assertEquals(expenses,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_expense",Long.class));

  auth(manager);var resolved=service.resolveHandover(handover.getId(),new Resolution("Physical count acknowledged; investigation retained externally"));assertEquals("RESOLVED",resolved.getStatus());assertFalse(finance.exceptions(1,100).getItems().stream().anyMatch(e->"CASH_HANDOVER".equals(e.getSourceType())&&handover.getId().equals(e.getSourceId())));
  auth(next);service.countClose(b.shift().getId(),new Count(new BigDecimal("1620.00"),null));service.close(b.shift().getId(),new Count(new BigDecimal("1620.00"),null));SecurityContextHolder.clearContext();
 }
 private long user(String...roles){String in=String.join(",",java.util.Arrays.stream(roles).map(x->"'"+x+"'").toList());return jdbc.queryForObject("SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.status=1 AND r.role_code IN ("+in+") ORDER BY u.id LIMIT 1",Long.class);}
 private long anotherOperational(long excluded){return jdbc.queryForObject("SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.status=1 AND r.role_code IN ('STAFF','MANAGER','OWNER','SUPER_ADMIN') AND u.id<>? ORDER BY u.id LIMIT 1",Long.class,excluded);}
 private void auth(long id){var roles=jdbc.queryForList("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=?",String.class,id);var a=new UsernamePasswordAuthenticationToken("handover-"+id,"n/a",roles.stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList());a.setDetails(id);SecurityContextHolder.getContext().setAuthentication(a);}
}
