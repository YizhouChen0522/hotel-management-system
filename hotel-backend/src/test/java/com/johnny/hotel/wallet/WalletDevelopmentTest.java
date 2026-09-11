package com.johnny.hotel.wallet;

import com.johnny.hotel.HotelBackendApplication;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.service.SysUserService;
import com.johnny.hotel.support.*;
import com.johnny.hotel.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.redis.core.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class WalletDevelopmentTest extends WalletDevelopmentFixture {
    @Test void migrationAndStartup()throws Exception {
        assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='10'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u LEFT JOIN wallet w ON w.user_id=u.id WHERE w.id IS NULL",Integer.class));
        for(long id:created){var w=wallets.byUser(id);assertEquals(0,w.getBalance().signum());assertEquals(1,w.getStatus());assertEquals("CNY",w.getCurrency());}
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());
    }
    @Test void databaseUniquenessAndNoArbitraryBalanceOverwrite(){
        assertThrows(Exception.class,()->wallets.open(uid("CUSTOMER")));
        assertThrows(Exception.class,()->jdbc.update("UPDATE wallet SET balance=99 WHERE id=?",wid("CUSTOMER")));
        assertThrows(Exception.class,()->jdbc.update("UPDATE wallet SET balance=-1 WHERE id=?",wid("CUSTOMER")));
        assertThrows(Exception.class,()->jdbc.update("DELETE FROM sys_user WHERE id=?",uid("CUSTOMER")));
        assertThrows(Exception.class,()->opening.openForNewUser(uid("CUSTOMER")));
        assertThrows(Exception.class,()->posting.creditTopUp(wallets.byUser(uid("CUSTOMER")),new WalletTopUp(),uid("STAFF"),"some_key"));
        invariant(wid("CUSTOMER"));
    }
    @Test void successCreatesExactlyOneLedgerAndRetriesAreIdempotent(){
        var top=pending("CUSTOMER","create_01");assertEquals(TopUpStatus.PENDING,top.status());assertEquals(0,wallets.find(top.walletId()).getBalance().signum());
        assertEquals(top.id(),service.createTopUp(top.walletId(),amount("12.34","create_01")).id());
        assertThrows(Exception.class,()->service.createTopUp(top.walletId(),amount("13","create_01")));
        as("STAFF");var result=service.confirm(top.walletId(),top.id(),decision("confirm_01"));assertEquals(TopUpStatus.SUCCESS,result.status());
        assertEquals(result,service.confirm(top.walletId(),top.id(),decision("confirm_01")));
        assertThrows(Exception.class,()->service.confirm(top.walletId(),top.id(),decision("different_01")));
        var rows=wallets.transactions(top.walletId(),0);assertEquals(1,rows.size());var row=rows.get(0);
        assertEquals(new BigDecimal("12.34"),row.getAmount());assertEquals(new BigDecimal("0.00"),row.getBalanceBefore());assertEquals(new BigDecimal("12.34"),row.getBalanceAfter());assertEquals(top.id(),row.getSourceId());assertEquals(uid("STAFF"),row.getOperatorUserId());invariant(top.walletId());
        assertThrows(Exception.class,()->jdbc.update("UPDATE wallet_transaction SET amount=22 WHERE id=?",row.getId()));
        assertThrows(Exception.class,()->jdbc.update("UPDATE wallet_top_up SET status=0 WHERE id=?",top.id()));
    }
    @Test void rejectionIsTerminalAndDoesNotCredit(){var top=pending("CUSTOMER","create_01");as("MANAGER");var rejected=service.reject(top.walletId(),top.id(),decision("reject_01"));assertEquals(TopUpStatus.REJECTED,rejected.status());assertEquals(rejected,service.reject(top.walletId(),top.id(),decision("reject_01")));assertThrows(Exception.class,()->service.confirm(top.walletId(),top.id(),decision("confirm_01")));assertEquals(0,wallets.find(top.walletId()).getBalance().signum());invariant(top.walletId());}
    @Test void blockedOnlyStopsNewTopUpsAndPendingConfirmation(){
        var first=pending("CUSTOMER","create_01");as("STAFF");service.confirm(first.walletId(),first.id(),decision("confirm_01"));var second=pending("CUSTOMER","create_02");
        as("MANAGER");service.status(first.walletId(),state(WalletStatus.BLOCKED));assertThrows(Exception.class,()->service.confirm(second.walletId(),second.id(),decision("confirm_02")));
        as("CUSTOMER");assertEquals(new BigDecimal("12.34"),service.mine().balance());assertEquals(1,service.transactions(first.walletId(),0).size());assertEquals(2,service.topUps(first.walletId(),0).size());assertThrows(Exception.class,()->service.createTopUp(first.walletId(),amount("1","create_03")));
        as("STAFF");service.status(first.walletId(),state(WalletStatus.ACTIVE));service.confirm(second.walletId(),second.id(),decision("confirm_02"));assertEquals(new BigDecimal("24.68"),wallets.find(first.walletId()).getBalance());invariant(first.walletId());
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN"})
    void mvcOwnershipRoleMatrixAndIdor(String actor)throws Exception{
        long self=wid(actor),customer=wid("OTHER_CUSTOMER"),employee=wid(actor.equals("STAFF")?"MANAGER":"STAFF");
        mvc.perform(get("/api/wallets/me").with(authentication(auth(actor)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(self));
        for(String suffix:new String[]{"","/transactions","/top-ups"}) {
            mvc.perform(get("/api/wallets/"+self+suffix).with(authentication(auth(actor)))).andExpect(status().isOk());
            mvc.perform(get("/api/wallets/"+customer+suffix).with(authentication(auth(actor)))).andExpect(status().is(Set.of("STAFF","MANAGER","OWNER","SUPER_ADMIN").contains(actor)?200:403));
            mvc.perform(get("/api/wallets/"+employee+suffix).with(authentication(auth(actor)))).andExpect(status().is(actor.equals("SUPER_ADMIN")?200:403));
        }
        mvc.perform(get("/api/wallets/by-user/"+uid("OTHER_CUSTOMER")).with(authentication(auth(actor)))).andExpect(status().is(Set.of("STAFF","MANAGER","OWNER","SUPER_ADMIN").contains(actor)?200:403));
        mvc.perform(post("/api/wallets/"+employee+"/top-ups").with(authentication(auth(actor))).contentType("application/json").content("{\"amount\":1,\"requestKey\":\"create_01\"}")).andExpect(status().isForbidden());
        as(actor);assertDoesNotThrow(()->service.createTopUp(self,amount("1","create_01")));
    }
    @ParameterizedTest @ValueSource(strings={"STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN"})
    void reviewAndBlockEmployeeOnlyBySuperAdmin(String actor){
        var top=pending("CUSTOMER","create_01");as(actor);if(actor.equals("HR_ADMIN")){assertThrows(Exception.class,()->service.confirm(top.walletId(),top.id(),decision("confirm_01")));assertThrows(Exception.class,()->service.status(top.walletId(),state(WalletStatus.BLOCKED)));}
        else {service.status(top.walletId(),state(WalletStatus.BLOCKED));service.status(top.walletId(),state(WalletStatus.ACTIVE));service.confirm(top.walletId(),top.id(),decision("confirm_01"));}
        var employee=pending("STAFF","create_02");as(actor);
        if(actor.equals("SUPER_ADMIN")){service.status(employee.walletId(),state(WalletStatus.BLOCKED));service.status(employee.walletId(),state(WalletStatus.ACTIVE));service.confirm(employee.walletId(),employee.id(),decision("confirm_02"));}
        else {assertThrows(Exception.class,()->service.status(employee.walletId(),state(WalletStatus.BLOCKED)));assertThrows(Exception.class,()->service.confirm(employee.walletId(),employee.id(),decision("confirm_02")));}
        invariant(top.walletId());invariant(employee.walletId());
    }
    @Test void noSelfBlockCustomerNoSelfReviewSuperAdminAndCrossWalletSource(){
        var customer=pending("CUSTOMER","create_01");assertThrows(Exception.class,()->service.status(customer.walletId(),state(WalletStatus.BLOCKED)));
        var top=pending("SUPER_ADMIN","create_02");assertThrows(Exception.class,()->service.confirm(top.walletId(),top.id(),decision("confirm_02")));
        assertThrows(Exception.class,()->service.confirm(customer.walletId(),top.id(),decision("confirm_03")));invariant(customer.walletId());invariant(top.walletId());
    }
    @ParameterizedTest @ValueSource(strings={"WalletPostingMapper.append","WalletPostingMapper.credit","WalletTopUpMapper.resolve","SysAuditLogMapper.insert"})
    void confirmationFailureRollsBackEveryFinancialWrite(String statement){var top=pending("CUSTOMER","create_01");as("STAFF");gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->service.confirm(top.walletId(),top.id(),decision("confirm_01")));assertEquals(0,wallets.find(top.walletId()).getBalance().signum());assertEquals(0,wallets.transactions(top.walletId(),0).size());assertEquals(TopUpStatus.PENDING,service.topUps(top.walletId(),0).get(0).status());invariant(top.walletId());}
    @Test void duplicateResolutionKeyRollsBackSecondCredit(){var first=pending("CUSTOMER","create_01");var second=pending("CUSTOMER","create_02");as("STAFF");service.confirm(first.walletId(),first.id(),decision("confirm_01"));assertThrows(Exception.class,()->service.confirm(second.walletId(),second.id(),decision("confirm_01")));assertEquals(new BigDecimal("12.34"),wallets.find(first.walletId()).getBalance());assertEquals(TopUpStatus.PENDING,service.topUps(first.walletId(),0).get(1).status());invariant(first.walletId());}
    @Test void concurrentConfirmCreditsOnce()throws Exception{
        var top=pending("CUSTOMER","create_01");var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var futures=new ArrayList<Future<WalletViews.TopUp>>();
        try{for(int i=0;i<8;i++)futures.add(pool.submit(()->{as("STAFF");try{start.await();return service.confirm(top.walletId(),top.id(),decision("confirm_01"));}finally{SecurityContextHolder.clearContext();}}));start.countDown();for(var f:futures)assertEquals(TopUpStatus.SUCCESS,f.get(15,TimeUnit.SECONDS).status());assertEquals(1,wallets.transactions(top.walletId(),0).size());assertEquals(new BigDecimal("12.34"),wallets.find(top.walletId()).getBalance());invariant(top.walletId());}
        finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void concurrentCreateUsesDatabaseIdempotency()throws Exception{
        long id=wid("CUSTOMER");var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);var futures=new ArrayList<Future<Long>>();
        try{for(int i=0;i<4;i++)futures.add(pool.submit(()->{as("CUSTOMER");try{start.await();return service.createTopUp(id,amount("12.34","create_01")).id();}finally{SecurityContextHolder.clearContext();}}));start.countDown();var ids=new HashSet<Long>();for(var f:futures)ids.add(f.get(15,TimeUnit.SECONDS));assertEquals(1,ids.size());assertEquals(0,wallets.find(id).getBalance().signum());}
        finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void blockRacingConfirmationUsesSameWalletLock()throws Exception{
        var top=pending("CUSTOMER","create_01");var pool=Executors.newFixedThreadPool(2);gate.arm("wallet-blocker","WalletMapper.status",false);
        try{var block=pool.submit(()->{Thread.currentThread().setName("wallet-blocker");as("MANAGER");try{return service.status(top.walletId(),state(WalletStatus.BLOCKED));}finally{SecurityContextHolder.clearContext();}});SqlGate.await(gate.reached);
            var confirm=pool.submit(()->{as("STAFF");try{return service.confirm(top.walletId(),top.id(),decision("confirm_01"));}finally{SecurityContextHolder.clearContext();}});
            assertThrows(TimeoutException.class,()->confirm.get(100,TimeUnit.MILLISECONDS));gate.release.countDown();block.get(15,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->confirm.get(15,TimeUnit.SECONDS));assertEquals(0,wallets.find(top.walletId()).getBalance().signum());invariant(top.walletId());
        }finally{gate.clear();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void registrationAndWalletFailureAreOneTransaction(boolean employee){
        var customer=customerRequest();var staff=employeeRequest("STAFF");gate.arm(Thread.currentThread().getName(),"WalletMapper.open",true);
        assertThrows(Exception.class,()->{if(employee)users.registerEmployee(staff);else users.registerCustomer(customer);});
        String name=employee?staff.getUsername():customer.getUsername();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?",Integer.class,name));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wallet w LEFT JOIN sys_user u ON u.id=w.user_id WHERE u.id IS NULL",Integer.class));
    }
    @Test void registrationApprovalDisableAndRoleChangesPreserveWallet(){
        var request=employeeRequest("STAFF");long user=users.registerEmployee(request).getId();created.add(user);long wallet=wallets.byUser(user).getId();
        assertEquals(2,users.getUserById(user).getStatus());users.approveUser(user,uid("HR_ADMIN"));assertEquals(1,users.getUserById(user).getStatus());assertEquals(wallet,wallets.byUser(user).getId());
        users.disableUser(user,uid("SUPER_ADMIN"));assertEquals(wallet,wallets.byUser(user).getId());assertEquals(1,wallets.byUser(user).getStatus());users.enableUser(user,uid("SUPER_ADMIN"));
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",user);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='MANAGER'",user);assertEquals(wallet,wallets.byUser(user).getId());
        assertThrows(Exception.class,()->users.registerEmployee(request));
    }
    @Test void actualJwtAndMvcValidationStillWork()throws Exception{
        var user=users.getUserById(uid("CUSTOMER"));var login=new LoginRequest();login.setEmail(user.getEmail());login.setPassword("wallet-test-only");String token=users.login(login).getToken();
        mvc.perform(get("/api/wallets/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(user.getId()));
        mvc.perform(get("/api/wallets/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wallets/"+wid("CUSTOMER")+"/top-ups").with(authentication(auth("CUSTOMER"))).contentType("application/json").content("{\"amount\":0.001,\"requestKey\":\"create_01\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/wallets/"+wid("CUSTOMER")+"/balance").with(authentication(auth("SUPER_ADMIN"))).contentType("application/json").content("{\"balance\":999}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/wallets/"+wid("CUSTOMER")+"/transactions/1").with(authentication(auth("SUPER_ADMIN")))).andExpect(status().isNotFound());
    }
    @Test void differentTopUpsConcurrentCreditsUseCurrentBalance()throws Exception{
        var first=pending("CUSTOMER","create_01");var second=pending("CUSTOMER","create_02");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{as("STAFF");try{start.await();return service.confirm(first.walletId(),first.id(),decision("confirm_01"));}finally{SecurityContextHolder.clearContext();}});
            var b=pool.submit(()->{as("MANAGER");try{start.await();return service.confirm(second.walletId(),second.id(),decision("confirm_02"));}finally{SecurityContextHolder.clearContext();}});
            start.countDown();a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);assertEquals(new BigDecimal("24.68"),wallets.find(first.walletId()).getBalance());invariant(first.walletId());
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void creditWithinAnExistingOldSnapshotStillUsesLockedCurrentState()throws Exception{
        var first=pending("CUSTOMER","create_01");var second=pending("CUSTOMER","create_02");var pool=Executors.newSingleThreadExecutor();
        try{new TransactionTemplate(txManager).executeWithoutResult(s->{
            assertEquals(0,jdbc.queryForObject("SELECT balance FROM wallet WHERE id=?",BigDecimal.class,first.walletId()).signum());
            try{pool.submit(()->{as("STAFF");try{return service.confirm(first.walletId(),first.id(),decision("confirm_01"));}finally{SecurityContextHolder.clearContext();}}).get(15,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}
            as("MANAGER");service.confirm(second.walletId(),second.id(),decision("confirm_02"));
        });assertEquals(new BigDecimal("24.68"),wallets.find(first.walletId()).getBalance());invariant(first.walletId());}
        finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void blockedPendingMayBeRejectedAndResolutionAuditRollsBackStatus(){
        var top=pending("CUSTOMER","create_01");as("STAFF");gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);
        assertThrows(Exception.class,()->service.status(top.walletId(),state(WalletStatus.BLOCKED)));assertEquals(1,wallets.find(top.walletId()).getStatus());
        service.status(top.walletId(),state(WalletStatus.BLOCKED));assertEquals(TopUpStatus.REJECTED,service.reject(top.walletId(),top.id(),decision("reject_01")).status());invariant(top.walletId());
    }
    @Test void balanceOverflowCannotResolveOrPost(){
        as("CUSTOMER");var first=service.createTopUp(wid("CUSTOMER"),amount("9999999999.99","create_01"));var second=service.createTopUp(wid("CUSTOMER"),amount("0.01","create_02"));
        as("STAFF");service.confirm(first.walletId(),first.id(),decision("confirm_01"));assertThrows(Exception.class,()->service.confirm(second.walletId(),second.id(),decision("confirm_02")));
        assertEquals(1,wallets.transactions(first.walletId(),0).size());assertEquals(TopUpStatus.PENDING,service.topUps(first.walletId(),0).get(1).status());invariant(first.walletId());
    }
    @Test void databaseRolesOverrideStaleOrForgedJwtAuthorities()throws Exception{
        var fake=new UsernamePasswordAuthenticationToken("test",null,List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));fake.setDetails(uid("CUSTOMER"));
        mvc.perform(get("/api/wallets/"+wid("STAFF")).with(authentication(fake))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",uid("MANAGER"));
        mvc.perform(get("/api/wallets/"+wid("CUSTOMER")).with(authentication(auth("MANAGER")))).andExpect(status().isForbidden());
        users.disableUser(uid("STAFF"),uid("SUPER_ADMIN"));assertNotNull(wallets.byUser(uid("STAFF")));
        mvc.perform(get("/api/wallets/me").with(authentication(auth("STAFF")))).andExpect(status().isForbidden());
    }
    @Test void employeeRejectionPreservesZeroWalletAndCustomerDisableDoesNotChangeIt(){
        var request=employeeRequest("STAFF");long id=users.registerEmployee(request).getId();created.add(id);long wallet=wallets.byUser(id).getId();users.rejectUser(id,uid("HR_ADMIN"));assertEquals(wallet,wallets.byUser(id).getId());assertEquals(0,wallets.byUser(id).getBalance().signum());
        long customerWallet=wid("CUSTOMER");users.disableUser(uid("CUSTOMER"),uid("SUPER_ADMIN"));assertEquals(customerWallet,wallets.byUser(uid("CUSTOMER")).getId());assertEquals(1,wallets.byUser(uid("CUSTOMER")).getStatus());
    }
}
