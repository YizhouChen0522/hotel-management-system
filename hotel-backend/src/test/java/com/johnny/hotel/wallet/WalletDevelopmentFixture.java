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

@SpringBootTest(classes=HotelBackendApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"hotel.wallet.dev.fixture=true","debug=false","logging.level.org.springframework=WARN",
                "logging.level.com.johnny=WARN","logging.level.org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(WalletDevelopmentFixture.Config.class)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
abstract class WalletDevelopmentFixture {
    static final String PREFIX="hotel:wallet-test:"+UUID.randomUUID()+":";
    @DynamicPropertySource static void development(DynamicPropertyRegistry r) {
        var e=WalletDevelopmentGuard.settings();
        r.add("spring.datasource.url",WalletDevelopmentGuard::url);r.add("spring.datasource.username",()->e.get("DB_USERNAME"));r.add("spring.datasource.password",()->e.get("DB_PASSWORD"));
        r.add("spring.data.redis.host",()->"localhost");r.add("spring.data.redis.port",()->6379);r.add("spring.data.redis.password",()->e.get("REDIS_PASSWORD"));
        r.add("hotel.redis.prefix",()->PREFIX);
    }
    @TestConfiguration static class Config {@Bean SqlGate walletSqlGate(){return new SqlGate();}}
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletService service;
    @Autowired WalletMapper wallets;
    @Autowired WalletOpeningService opening;
    @Autowired WalletPostingService posting;
    @Autowired WalletTopUpMapper requests;
    @Autowired SysUserService users;
    @Autowired SqlGate gate;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired PlatformTransactionManager txManager;
    @Autowired StringRedisTemplate redis;
    @Value("${local.server.port}") int port;
    final Map<String,Long> actors=new LinkedHashMap<>();
    final List<Long> created=new ArrayList<>();
    final String run="wt_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
    int sequence;

    RegisterCustomerRequest customerRequest(){var r=new RegisterCustomerRequest();String name=run+"_"+(sequence++);r.setUsername(name);r.setEmail(name+"@example.test");r.setPassword("wallet-test-only");r.setRealName("Wallet test fixture");return r;}
    RegisterEmployeeRequest employeeRequest(String role){var r=new RegisterEmployeeRequest();String name=run+"_"+(sequence++);r.setUsername(name);r.setEmail(name+"@example.test");r.setPassword("wallet-test-only");r.setRealName("Wallet test fixture");r.setApplyRoleCode(role);return r;}
    @BeforeEach void seedOnlyNewUsers(){
        assertEquals("hotel_management",jdbc.queryForObject("SELECT DATABASE()",String.class));assertEquals(3306,jdbc.queryForObject("SELECT @@port",Integer.class));
        for(String role:List.of("CUSTOMER","OTHER_CUSTOMER","STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN")) {
            long id=role.contains("CUSTOMER")?users.registerCustomer(customerRequest()).getId():users.registerEmployee(employeeRequest(role.equals("SUPER_ADMIN")?"STAFF":role)).getId();
            created.add(id);actors.put(role,id);
            if(!role.contains("CUSTOMER")) {
                jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",id);
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);
            }
        }
    }
    @AfterEach void cleanupOnlyCreatedRows(){
        gate.clear();SecurityContextHolder.clearContext();
        // Privileged test teardown only: no application delete API or mapper exists for money history.
        for(long id:created) jdbc.update("DELETE FROM wallet_transaction WHERE wallet_id IN (SELECT id FROM wallet WHERE user_id=?)",id);
        for(long id:created) jdbc.update("DELETE FROM wallet_top_up WHERE wallet_id IN (SELECT id FROM wallet WHERE user_id=?)",id);
        for(long id:created) {
            jdbc.update("DELETE FROM wallet WHERE user_id=?",id);
            jdbc.update("DELETE FROM sys_audit_log WHERE target_user_id=? OR operator_id=?",id,id);
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",id);
            jdbc.update("DELETE FROM sys_user WHERE id=?",id);
        }
        try(var keys=redis.scan(ScanOptions.scanOptions().match(PREFIX+"*").count(100).build())) {keys.forEachRemaining(redis::delete);}
    }
    long uid(String role){return actors.get(role);}
    long wid(String role){return wallets.byUser(uid(role)).getId();}
    UsernamePasswordAuthenticationToken auth(String role){var a=new UsernamePasswordAuthenticationToken("wallet-test",null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));a.setDetails(uid(role));return a;}
    void as(String role){SecurityContextHolder.getContext().setAuthentication(auth(role));}
    WalletRequests.TopUp amount(String amount,String key){return WalletRequests.TopUp.builder().amount(new BigDecimal(amount)).requestKey(key).build();}
    WalletRequests.Decision decision(String key){return WalletRequests.Decision.builder().requestKey(key).reason("Cash receipt verified in test").build();}
    WalletRequests.Status state(WalletStatus state){return WalletRequests.Status.builder().status(state).reason("Test status change").build();}
    WalletViews.TopUp pending(String role,String key){as(role);return service.createTopUp(wid(role),amount("12.34",key));}
    void invariant(long wallet){var w=wallets.find(wallet);assertTrue(w.getBalance().signum()>=0);assertEquals(0,w.getBalance().compareTo(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_transaction WHERE wallet_id=?",BigDecimal.class,wallet)));}

}
