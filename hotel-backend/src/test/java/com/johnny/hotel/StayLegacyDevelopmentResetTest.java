package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit, one-time removal of the inventoried pre-Guest mock reservations, never a startup hook. */
@EnabledIfSystemProperty(named="hotel.stay.legacy.reset",matches="true")
class StayLegacyDevelopmentResetTest {
    @Test void removeOnlyVerifiedUnfundedLegacyReservations() throws Exception {
        var settings=WalletDevelopmentGuard.settings();
        try(var c=DriverManager.getConnection(WalletDevelopmentGuard.url(),settings.get("DB_USERNAME"),settings.get("DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement();var r=s.executeQuery("SELECT id FROM booking ORDER BY id FOR UPDATE")) {
                    int count=0;while(r.next())count++;assertEquals(11,count,"Legacy inventory changed; do not delete");
                }
                for(String table:List.of("payment","refund","wallet_transaction","guest_registration","booking_guest","guest_profile","stay","deposit_payment","deposit_refund","deposit_transfer","reservation_deposit_account","stay_adjustment","stay_history","cleaning_record","housekeeping_inspection","room_repair_task","room_work_order","damage_assessment","guest_service_order","guest_purchase","invoice","room_billing_event","expense_registration","stay_extension_nightly_rate"))
                    assertEquals(0,count(c,table),"Unexpected business facts in "+table+"; abort cleanup");
                var baseline=new LinkedHashMap<String,Long>();
                for(String table:List.of("sys_user","sys_role","department","room","room_type","room_rate","wallet","charge_catalog","flyway_schema_history"))baseline.put(table,count(c,table));
                var taskIds=new ArrayList<Long>();
                try(var s=c.createStatement();var r=s.executeQuery("SELECT task_id FROM room_turnover_task")){while(r.next())taskIds.add(r.getLong(1));}
                try(var s=c.createStatement()) {
                    // Preserve room identity/configuration; remove only the occupancy state of discarded mock stays.
                    s.executeUpdate("UPDATE room SET status=3 WHERE status IN (2,4) AND id IN (SELECT assigned_room_id FROM booking WHERE assigned_room_id IS NOT NULL)");
                    s.executeUpdate("DELETE FROM room_turnover_task WHERE assignment_id IN (SELECT id FROM booking_room_assignment)");
                    s.executeUpdate("DELETE FROM folio_item WHERE folio_id IN (SELECT id FROM folio) ORDER BY id DESC");
                    s.executeUpdate("DELETE FROM folio WHERE booking_id IN (SELECT id FROM booking)");
                    s.executeUpdate("DELETE FROM booking_room_assignment WHERE booking_id IN (SELECT id FROM booking)");
                    s.executeUpdate("DELETE FROM booking_nightly_rate WHERE booking_id IN (SELECT id FROM booking)");
                    s.executeUpdate("DELETE FROM booking_price_version WHERE booking_id IN (SELECT id FROM booking)");
                    s.executeUpdate("DELETE FROM booking");
                }
                for(long task:taskIds)for(String table:List.of("task_record","todo","task_assignment","hotel_task")) {
                    try(var s=c.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("hotel_task")?"id":"task_id")+"=?")){s.setLong(1,task);s.executeUpdate();}
                }
                for(var e:baseline.entrySet())assertEquals(e.getValue().longValue(),count(c,e.getKey()),"Preset inventory changed");
                c.commit();System.out.println("Removed 11 unfunded pre-Guest mock reservations; protected preset rows retained.");
            } catch(Throwable failure) {c.rollback();throw failure;}
        }
    }
    private long count(Connection c,String table)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
}
