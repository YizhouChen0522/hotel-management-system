package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named="hotel.dynamic.bootstrap.preflight",matches="true")
class DynamicPricingBootstrapPreflightTest {
    @Test void inspectDevelopmentStateReadOnly() throws Exception {
        var settings=WalletDevelopmentGuard.settings();
        try(var connection=DriverManager.getConnection(WalletDevelopmentGuard.url(),settings.get("DB_USERNAME"),settings.get("DB_PASSWORD"));
            var statement=connection.createStatement()) {
            connection.setReadOnly(true);
            try(var rows=statement.executeQuery("SELECT p.id,p.version_no,p.name,p.status,p.created_by,(SELECT COUNT(*) FROM dynamic_occupancy_band WHERE policy_id=p.id),(SELECT COUNT(*) FROM dynamic_booking_window_band WHERE policy_id=p.id),(SELECT COUNT(*) FROM dynamic_pricing_cell WHERE policy_id=p.id) FROM dynamic_pricing_policy p ORDER BY p.id")) {
                while(rows.next()) System.out.printf("DYNAMIC_POLICY id=%d version=%d name=%s status=%d creator=%d occupancy=%d windows=%d cells=%d%n",
                        rows.getLong(1),rows.getInt(2),rows.getString(3),rows.getInt(4),rows.getLong(5),rows.getInt(6),rows.getInt(7),rows.getInt(8));
            }
            try(var rows=statement.executeQuery("SELECT version,description,checksum,success FROM flyway_schema_history WHERE version IN ('33','37','38','39') ORDER BY CAST(version AS UNSIGNED)")) {
                int count=0;
                while(rows.next()){count++;System.out.printf("MIGRATION version=%s description=%s checksum=%d success=%s%n",rows.getString(1),rows.getString(2),rows.getInt(3),rows.getBoolean(4));}
                assertTrue(count==4,"Expected V33, V37, V38 and V39 in development history");
            }
        }
    }
}
