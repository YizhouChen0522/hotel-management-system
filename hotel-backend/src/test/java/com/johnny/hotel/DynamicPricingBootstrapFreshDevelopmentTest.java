package com.johnny.hotel;

import com.johnny.hotel.dto.RegisterEmployeeRequest;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.mapper.SysRoleMapper;
import com.johnny.hotel.mapper.SysUserMapper;
import com.johnny.hotel.mapper.SysUserRoleMapper;
import com.johnny.hotel.pricing.DynamicPricingBootstrapRunner;
import com.johnny.hotel.pricing.DynamicPricingBootstrapService;
import com.johnny.hotel.service.SysUserService;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import com.johnny.hotel.wallet.WalletOpeningService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=HotelBackendApplication.class, properties={"debug=false","logging.level.org.springframework=WARN"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named="hotel.dynamic.bootstrap.fresh",matches="true")
class DynamicPricingBootstrapFreshDevelopmentTest {
    private static final String DATABASE="hotel_dynamic_bootstrap_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
    private static final String URL=WalletDevelopmentGuard.url().replace("/hotel_management?","/"+DATABASE+"?");
    private static final String USER=WalletDevelopmentGuard.settings().get("DB_USERNAME");
    private static final String PASSWORD=WalletDevelopmentGuard.settings().get("DB_PASSWORD");
    private static boolean created;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        try(var connection=DriverManager.getConnection(WalletDevelopmentGuard.url(),USER,PASSWORD);
            var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE `"+DATABASE+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            created=true;
        }
        registry.add("spring.datasource.url",()->URL);
        registry.add("spring.datasource.username",()->USER);
        registry.add("spring.datasource.password",()->PASSWORD);
        registry.add("spring.data.redis.password",()->WalletDevelopmentGuard.settings().get("REDIS_PASSWORD"));
        registry.add("hotel.redis.prefix",()->"hotel:dynamic-bootstrap-test:");
    }
    @AfterAll static void removeOnlyTemporaryDatabase() throws Exception {
        if(!created)return;
        try(var connection=DriverManager.getConnection(WalletDevelopmentGuard.url(),USER,PASSWORD);
            var statement=connection.createStatement()) {statement.execute("DROP DATABASE `"+DATABASE+"`");}
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired SysUserMapper userMapper;
    @Autowired SysRoleMapper roleMapper;
    @Autowired SysUserRoleMapper userRoles;
    @Autowired WalletOpeningService wallets;
    @Autowired PasswordEncoder encoder;
    @Autowired SysUserService users;
    @Autowired DynamicPricingBootstrapService bootstrap;
    @Autowired DynamicPricingBootstrapRunner runner;
    @Autowired PlatformTransactionManager transactions;
    private static long superAdmin, firstOwner, secondOwner;

    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private void completeTemplate(){assertEquals(1,count("dynamic_pricing_policy"));assertEquals(5,count("dynamic_occupancy_band"));
        assertEquals(4,count("dynamic_booking_window_band"));assertEquals(20,count("dynamic_pricing_cell"));
        assertEquals(0,jdbc.queryForObject("SELECT status FROM dynamic_pricing_policy LIMIT 1",Integer.class));
        assertEquals("Default Dynamic Pricing Template",jdbc.queryForObject("SELECT name FROM dynamic_pricing_policy LIMIT 1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT version_no FROM dynamic_pricing_policy LIMIT 1",Integer.class));
        assertEquals(0,new BigDecimal("0.8000").compareTo(jdbc.queryForObject("SELECT minimum_multiplier FROM dynamic_pricing_policy LIMIT 1",BigDecimal.class)));
        assertEquals(0,new BigDecimal("2.0000").compareTo(jdbc.queryForObject("SELECT maximum_multiplier FROM dynamic_pricing_policy LIMIT 1",BigDecimal.class)));
        assertEquals(java.util.List.of("0-39%:0:40","40-69%:40:70","70-84%:70:85","85-94%:85:95","95-100%:95:101"),
                jdbc.query("SELECT label,lower_inclusive,upper_exclusive FROM dynamic_occupancy_band ORDER BY sort_order",
                        (rs,n)->rs.getString(1)+":"+rs.getInt(2)+":"+rs.getInt(3)));
        assertEquals(java.util.List.of("0-7 days:0:7","8-14 days:8:14","15-30 days:15:30","31+ days:31:open"),
                jdbc.query("SELECT label,min_days,max_days FROM dynamic_booking_window_band ORDER BY sort_order",
                        (rs,n)->rs.getString(1)+":"+rs.getInt(2)+":"+(rs.getObject(3)==null?"open":rs.getInt(3))));
        var actual=jdbc.queryForList("SELECT c.adjustment_percent FROM dynamic_pricing_cell c JOIN dynamic_occupancy_band o ON o.id=c.occupancy_band_id JOIN dynamic_booking_window_band w ON w.id=c.booking_window_band_id ORDER BY o.sort_order,w.sort_order",BigDecimal.class);
        int[] original={-15,-10,-5,0,-5,0,0,0,20,15,15,10,30,25,25,20,40,35,35,30};
        for(int i=0;i<original.length;i++)assertEquals(0,BigDecimal.valueOf(original[i]).compareTo(actual.get(i)),"V37 matrix cell "+i);
    }
    private String createTable(String database,String table){return jdbc.queryForObject("SHOW CREATE TABLE `"+database+"`.`"+table+"`",(rs,row)->rs.getString(2))
            .replaceAll(" AUTO_INCREMENT=\\d+", "");}

    @Test @Order(1) void zeroUserStartupAndSchemaMatchDevelopment() {
        flyway.validate();assertEquals(0,count("sys_user"));assertEquals(0,count("dynamic_pricing_policy"));
        assertEquals(0,count("dynamic_occupancy_band"));assertEquals(0,count("dynamic_booking_window_band"));
        assertEquals(0,count("dynamic_pricing_cell"));
        for(String table:new String[]{"dynamic_pricing_control","dynamic_pricing_policy","dynamic_occupancy_band",
                "dynamic_booking_window_band","dynamic_pricing_cell","manual_rate_override","booking_nightly_rate"})
            assertEquals(createTable("hotel_management",table),createTable(DATABASE,table),table);
    }
    private long fixtureSuperAdmin(){return new TransactionTemplate(transactions).execute(status->{
        var user=SysUser.builder().username("sa_"+DATABASE).password(encoder.encode("temporary-test-only"))
                .realName("Temporary bootstrap administrator").status(1).build();
        assertEquals(1,userMapper.insert(user));wallets.openForNewUser(user.getId());
        assertEquals(1,userRoles.insertUserRole(user.getId(),roleMapper.selectByRoleCode("SUPER_ADMIN").getId()));
        return user.getId();
    });}
    private long pendingOwner(String suffix){var request=new RegisterEmployeeRequest();request.setUsername("o"+suffix+"_"+DATABASE);
        request.setEmail("o"+suffix+"_"+DATABASE+"@example.test");request.setPassword("temporary-test-only");
        request.setRealName("Temporary Owner");request.setApplyRoleCode("OWNER");
        return users.registerEmployee(request).getId();}
    private long approveOwner(String suffix){long id=pendingOwner(suffix);users.approveUser(id,superAdmin);return id;}

    @Test @Order(2) void firstRealApprovedOwnerCreatesOriginalDraft() {
        superAdmin=fixtureSuperAdmin();assertEquals(0,count("dynamic_pricing_policy"));
        firstOwner=pendingOwner("first");
        assertThrows(BusinessException.class,()->bootstrap.ensureDefaultTemplate(firstOwner));
        assertEquals(0,count("dynamic_pricing_policy"));
        users.approveUser(firstOwner,superAdmin);completeTemplate();
        assertEquals(firstOwner,jdbc.queryForObject("SELECT created_by FROM dynamic_pricing_policy LIMIT 1",Long.class));
        assertEquals("Default Dynamic Pricing Template",jdbc.queryForObject("SELECT name FROM dynamic_pricing_policy LIMIT 1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT version_no FROM dynamic_pricing_policy LIMIT 1",Integer.class));
    }
    @Test @Order(3) void secondAdminRetryAndStartupNeverDuplicate() {
        secondOwner=approveOwner("second");assertFalse(bootstrap.ensureDefaultTemplate(firstOwner));
        users.disableUser(secondOwner,superAdmin);
        assertThrows(BusinessException.class,()->bootstrap.ensureDefaultTemplate(secondOwner));
        users.enableUser(secondOwner,superAdmin);
        long rejected=pendingOwner("rejected");users.rejectUser(rejected,superAdmin);
        assertThrows(BusinessException.class,()->bootstrap.ensureDefaultTemplate(rejected));
        runner.run(new DefaultApplicationArguments(new String[0]));completeTemplate();
        assertEquals(firstOwner,jdbc.queryForObject("SELECT created_by FROM dynamic_pricing_policy LIMIT 1",Long.class));
    }
    private void resetOnlyTemporaryTemplate(){jdbc.update("DELETE FROM dynamic_pricing_cell");
        jdbc.update("DELETE FROM dynamic_occupancy_band");jdbc.update("DELETE FROM dynamic_booking_window_band");
        jdbc.update("DELETE FROM dynamic_pricing_policy");}
    @Test @Order(4) void concurrentAdministratorsCreateExactlyOneCompleteDraft() throws Exception {
        resetOnlyTemporaryTemplate();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> a=()->{start.await();return bootstrap.ensureDefaultTemplate(firstOwner);};
            Callable<Boolean> b=()->{start.await();return bootstrap.ensureDefaultTemplate(secondOwner);};
            var first=pool.submit(a);var second=pool.submit(b);start.countDown();
            assertNotEquals(first.get(25,TimeUnit.SECONDS),second.get(25,TimeUnit.SECONDS));completeTemplate();
        } finally {pool.shutdownNow();}
    }
    @Test @Order(5) void startupFallbackUsesEarliestRealAdministrator() {
        resetOnlyTemporaryTemplate();runner.run(new DefaultApplicationArguments(new String[0]));completeTemplate();
        assertEquals(superAdmin,jdbc.queryForObject("SELECT created_by FROM dynamic_pricing_policy LIMIT 1",Long.class));
        runner.run(new DefaultApplicationArguments(new String[0]));completeTemplate();
    }
    @Test @Order(6) void partialTemplateFailureRollsBackAndRetrySucceeds() {
        resetOnlyTemporaryTemplate();
        jdbc.execute("CREATE TRIGGER bootstrap_cell_failure BEFORE INSERT ON dynamic_pricing_cell "
                + "FOR EACH ROW SET NEW.adjustment_percent = NULL");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> bootstrap.ensureDefaultTemplate(firstOwner));
            assertEquals(0,count("dynamic_pricing_policy"));
            assertEquals(0,count("dynamic_occupancy_band"));
            assertEquals(0,count("dynamic_booking_window_band"));
            assertEquals(0,count("dynamic_pricing_cell"));
        } finally {
            jdbc.execute("DROP TRIGGER bootstrap_cell_failure");
        }
        assertTrue(bootstrap.ensureDefaultTemplate(firstOwner));
        completeTemplate();
    }
}
