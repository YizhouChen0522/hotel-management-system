package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly authorized recovery of the inventoried V33 partial DDL; never part of application startup. */
@EnabledIfSystemProperty(named="hotel.stay.v33.recovery",matches="true")
class StayV33RecoveryTest {
 @Test void completeOnlyUnappliedTailAndValidateHistory() throws Exception {
  var settings=WalletDevelopmentGuard.settings();
  Path file=Path.of("src/main/resources/db/migration/V33__reservation_stay_ownership.sql");
  try(var c=DriverManager.getConnection(WalletDevelopmentGuard.url(),settings.get("DB_USERNAME"),settings.get("DB_PASSWORD"))) {
   assertEquals(1,number(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='33' AND success=0"));
   assertEquals(0,number(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)>33"));
   for(String t:List.of("booking","stay","stay_guest","stay_room_assignment","folio","stay_adjustment","stay_history","deposit_transfer","deposit_payment","deposit_refund"))assertEquals(0,number(c,"SELECT COUNT(*) FROM "+t));
   assertEquals(1,column(c,"folio","stay_id"));assertEquals(1,column(c,"stay_room_assignment","stay_id"));
   assertEquals(1,column(c,"stay_adjustment","booking_id"));assertEquals(0,column(c,"stay_adjustment","stay_id"));
   var baseline=new LinkedHashMap<String,Long>();
   for(String t:List.of("sys_user","sys_role","department","room","room_type","room_rate","wallet","charge_catalog"))baseline.put(t,number(c,"SELECT COUNT(*) FROM "+t));
   int priorChecksum=(int)number(c,"SELECT checksum FROM flyway_schema_history WHERE version='32'");
   assertEquals(priorChecksum,checksum(Path.of("src/main/resources/db/migration/V32__deposit_currency_collation.sql")),"Checksum algorithm must match successful Flyway migration");
   long priorHistory=number(c,"SELECT SUM(COALESCE(checksum,0)) FROM flyway_schema_history WHERE version<>'33'");
   String source=Files.readString(file);String tail=source.substring(source.indexOf("ALTER TABLE stay_adjustment CHANGE COLUMN"));
   String delimiter=";";var sql=new StringBuilder();int count=0;
   for(String line:tail.split("\\R")) {
    String trimmed=line.trim();if(trimmed.startsWith("--")||trimmed.isEmpty())continue;
    if(trimmed.startsWith("DELIMITER ")){assertTrue(sql.toString().isBlank());delimiter=trimmed.substring(10);continue;}
    sql.append(line).append('\n');
    if(trimmed.endsWith(delimiter)){String statement=sql.toString().trim();statement=statement.substring(0,statement.length()-delimiter.length());try(var st=c.createStatement()){st.execute(statement);}sql.setLength(0);count++;}
   }
   assertTrue(sql.toString().isBlank());assertTrue(count>20);
   for(String t:List.of("folio","stay_adjustment","stay_extension_nightly_rate","room_billing_event","room_turnover_task","stayover_cleaning_request","cleaning_record","housekeeping_inspection","rework_cleaning_request","guest_service_order","guest_purchase","damage_assessment","stay_history")){assertEquals(1,column(c,t,"stay_id"));assertEquals(0,column(c,t,"booking_id"));}
   assertEquals(0,column(c,"booking","assigned_room_id"));assertEquals(1,column(c,"booking","reservation_source"));
   assertEquals(1,number(c,"SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_stay_previous'"));
   assertEquals(1,number(c,"SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='deposit_transfer_insert_guard'"));
   for(var e:baseline.entrySet())assertEquals(e.getValue().longValue(),number(c,"SELECT COUNT(*) FROM "+e.getKey()));
   assertEquals(priorHistory,number(c,"SELECT SUM(COALESCE(checksum,0)) FROM flyway_schema_history WHERE version<>'33'"));
   try(var st=c.prepareStatement("UPDATE flyway_schema_history SET success=1,checksum=? WHERE version='33' AND success=0")){st.setInt(1,checksum(file));assertEquals(1,st.executeUpdate());}
   System.out.println("V33 remaining DDL completed; only its failed history entry reconciled; preserved inventories verified.");
  }
  org.flywaydb.core.Flyway.configure().dataSource(WalletDevelopmentGuard.url(),settings.get("DB_USERNAME"),settings.get("DB_PASSWORD")).locations("filesystem:src/main/resources/db/migration").load().validate();
 }
 private long number(Connection c,String sql)throws SQLException{try(var st=c.createStatement();var r=st.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
 private long column(Connection c,String table,String col)throws SQLException{return number(c,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+table+"' AND column_name='"+col+"'");}
 private int checksum(Path p)throws Exception{var crc=new CRC32();for(String line:Files.readAllLines(p,StandardCharsets.UTF_8))crc.update(line.replace("\uFEFF","").getBytes(StandardCharsets.UTF_8));return (int)crc.getValue();}
}
