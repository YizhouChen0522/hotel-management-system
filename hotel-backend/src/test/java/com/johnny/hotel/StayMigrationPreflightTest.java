package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.DriverManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only inventory before the reservation/stay forward migration. No Spring/Flyway startup. */
@EnabledIfSystemProperty(named="hotel.wallet.dev.tests", matches="true")
class StayMigrationPreflightTest {
    @Test void inspectPartialMigrationReadOnly() throws Exception {
        var settings=WalletDevelopmentGuard.settings();
        try(var c=DriverManager.getConnection(WalletDevelopmentGuard.url(),settings.get("DB_USERNAME"),settings.get("DB_PASSWORD"));var st=c.createStatement()) {
            c.setReadOnly(true);
            try(var r=st.executeQuery("SHOW ENGINE INNODB STATUS")){if(r.next()){String text=r.getString(3);int a=text.indexOf("LATEST FOREIGN KEY ERROR");int b=text.indexOf("TRANSACTIONS",Math.max(0,a));if(a>=0)System.out.println(text.substring(a,b>a?b:Math.min(text.length(),a+4000)));}}

            for(String sql:List.of(
                "SELECT version,success FROM flyway_schema_history WHERE installed_rank>30 ORDER BY installed_rank",
                "SELECT table_name,column_name,column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('stay','booking_room_assignment','stay_room_assignment','folio','stay_adjustment','stay_history','booking') ORDER BY table_name,ordinal_position",
                "SELECT table_name,constraint_name,referenced_table_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL AND table_name IN ('stay','booking_room_assignment','stay_room_assignment','folio','refund','stay_adjustment','stay_extension_nightly_rate','room_billing_event','room_turnover_task','stayover_cleaning_request') GROUP BY table_name,constraint_name,referenced_table_name ORDER BY table_name,constraint_name",
                "SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
            )) try(var r=st.executeQuery(sql)){while(r.next()){var out=new StringBuilder();for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.append(r.getString(i)).append(' ');System.out.println(out);}}
        }
    }
    @Test void inventoryCurrentSchemaWithoutMutatingData() throws Exception {
        var settings=WalletDevelopmentGuard.settings();
        try(var connection=DriverManager.getConnection(WalletDevelopmentGuard.url(),settings.get("DB_USERNAME"),settings.get("DB_PASSWORD"));
            var statement=connection.createStatement()) {
            connection.setReadOnly(true);
            try(var rows=statement.executeQuery("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
                assertTrue(rows.next()); assertTrue(rows.getBoolean(2));
                System.out.println("Current successful migration: V"+rows.getString(1));
            }
            boolean migrated;
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='folio' AND column_name='stay_id'")) {rows.next();migrated=rows.getInt(1)==1;}
            for(String table:List.of("sys_user","sys_role","department","room","room_type","room_rate","wallet","wallet_transaction","charge_catalog","booking","stay_room_assignment","folio","payment","refund","guest_registration","booking_guest","guest_profile","stay_adjustment","stay_history","room_turnover_task","cleaning_record","housekeeping_inspection","room_repair_task","room_work_order","damage_assessment","guest_service_order","guest_purchase","invoice","room_billing_event","folio_item","expense_registration","stay_extension_nightly_rate")) {
                String actual=table.equals("stay_room_assignment")&&!migrated?"booking_room_assignment":table;
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+actual)) {
                    assertTrue(rows.next()); System.out.println("Preserved inventory "+table+": "+rows.getLong(1));
                }
            }
            String join=migrated?"LEFT JOIN stay s ON s.booking_id=b.id LEFT JOIN folio f ON f.stay_id=s.id":"LEFT JOIN folio f ON f.booking_id=b.id";
            try(var rows=statement.executeQuery("SELECT b.status,COUNT(DISTINCT b.id),COUNT(p.id),COALESCE(SUM(p.amount),0) FROM booking b "+join+" LEFT JOIN payment p ON p.folio_id=f.id GROUP BY b.status ORDER BY b.status")) {
                while(rows.next()) System.out.println("Reservation status "+rows.getInt(1)+": bookings="+rows.getLong(2)+", payments="+rows.getLong(3)+", paid="+rows.getBigDecimal(4));
            }
        }
    }
}
