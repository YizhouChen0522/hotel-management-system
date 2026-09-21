package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** One-time, opt-in checksum realignment after the authorized V37 seed extraction. */
@EnabledIfSystemProperty(named="hotel.dynamic.v37.directed.repair",matches="true")
class DynamicPricingV37DirectedRepairTest {
    private record History(int rank,String description,Integer checksum,boolean success) {}
    private Map<String,History> history(JdbcTemplate jdbc){
        var result=new LinkedHashMap<String,History>();
        jdbc.query("SELECT version,installed_rank,description,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",rs->{
            result.put(rs.getString(1),new History(rs.getInt(2),rs.getString(3),rs.getInt(4),rs.getBoolean(5)));
        });
        return result;
    }
    private Map<String,List<Map<String,Object>>> template(JdbcTemplate jdbc){
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("dynamic_pricing_policy","dynamic_occupancy_band","dynamic_booking_window_band","dynamic_pricing_cell","dynamic_pricing_control"))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"));
        return result;
    }
    @Test void repairOnlyTheVerifiedV37Checksum() {
        var settings=WalletDevelopmentGuard.settings();
        String url=WalletDevelopmentGuard.url(),user=settings.get("DB_USERNAME"),password=settings.get("DB_PASSWORD");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        assertEquals("hotel_management",jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(3306,jdbc.queryForObject("SELECT @@port",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Integer.class));
        var before=history(jdbc);var data=template(jdbc);
        assertEquals(39,before.size());
        assertEquals(1042387804,before.get("37").checksum());
        assertEquals(1,data.get("dynamic_pricing_policy").size());
        assertEquals(5,data.get("dynamic_occupancy_band").size());
        assertEquals(4,data.get("dynamic_booking_window_band").size());
        assertEquals(20,data.get("dynamic_pricing_cell").size());
        var flyway=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load();
        var validation=flyway.validateWithResult();
        assertFalse(validation.validationSuccessful);
        assertEquals(1,validation.invalidMigrations.size());
        assertEquals("37",validation.invalidMigrations.get(0).version);

        flyway.repair();

        var after=history(jdbc);
        assertEquals(before.keySet(),after.keySet());
        for(var version:before.keySet()) {
            if("37".equals(version)) {
                assertEquals(before.get(version).rank(),after.get(version).rank());
                assertEquals(before.get(version).description(),after.get(version).description());
                assertEquals(before.get(version).success(),after.get(version).success());
                assertNotEquals(before.get(version).checksum(),after.get(version).checksum());
            }else assertEquals(before.get(version),after.get(version),"Unexpected history change in V"+version);
        }
        assertEquals(data,template(jdbc),"Existing Dynamic Pricing template must remain byte-for-byte equivalent by row values");
        flyway.validate();
        System.out.printf("DIRECTED_V37_REPAIR old=%d new=%d v33=%d v38=%d v39=%d%n",before.get("37").checksum(),after.get("37").checksum(),
                after.get("33").checksum(),after.get("38").checksum(),after.get("39").checksum());
    }
}
