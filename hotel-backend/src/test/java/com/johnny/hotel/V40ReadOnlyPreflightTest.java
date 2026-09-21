package com.johnny.hotel;

import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="hotel.v40.preflight",matches="true")
class V40ReadOnlyPreflightTest {
 private int scalar(java.sql.Connection c,String sql)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
 private String text(java.sql.Connection c,String sql)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
 @Test void reportOnly()throws Exception{
  var e=WalletDevelopmentGuard.settings();
  try(var c=DriverManager.getConnection(WalletDevelopmentGuard.url()+"&sessionVariables=transaction_read_only=ON",e.get("DB_USERNAME"),e.get("DB_PASSWORD"))){
  c.setReadOnly(true);
  int history=scalar(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='40' AND success=1");
  int v41=scalar(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='41' AND success=1");
  int column=scalar(c,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='booking' AND column_name='portal_request_key'");
  int lockTable=scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='portal_reservation_request_lock'");
  String trigger=text(c,"SELECT ACTION_STATEMENT FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='wallet_transaction_insert_guard'");
  String receiptTrigger=text(c,"SELECT ACTION_STATEMENT FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='deposit_payment_insert_guard'");
  System.out.println("V40_PREFLIGHT history="+history+" v41="+v41+" column="+column+" lockTable="+lockTable+" triggerType6="+trigger.contains("transaction_type=6")+" walletReceipt="+receiptTrigger.contains("WALLET"));
  assertEquals(1,history);assertEquals(1,v41);assertEquals(1,column);assertEquals(1,lockTable);assertTrue(trigger.contains("transaction_type=6"));assertTrue(receiptTrigger.contains("WALLET"));
  }
 }
}
