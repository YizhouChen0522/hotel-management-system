package com.johnny.hotel;
import com.johnny.hotel.enums.FolioStatus;
import com.johnny.hotel.stay.*;
import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.service.support.BillingRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class StayAdjustmentRulesTest {
    @ParameterizedTest @CsvSource({"100,0","0,1","-0.01,2"}) void balanceDeterminesStableCode(String amount,int code){var s=FolioStatus.forBalance(new BigDecimal(amount));assertEquals(code,s.getCode());assertEquals(s,FolioStatus.fromCode(code));}
    @Test void unknownCodesRejected(){assertThrows(IllegalArgumentException.class,()->FolioStatus.fromCode(3));assertThrows(IllegalArgumentException.class,()->StayAdjustmentType.fromCode(0));assertEquals(1,StayAdjustmentType.EXTENSION.getCode());assertEquals(2,StayAdjustmentType.EARLY_CHECKOUT.getCode());assertEquals(3,StayAdjustmentType.LATE_CHECKOUT.getCode());}
    @Test void checkoutTimesHaveOneConfigurableSource(){var t=new StayTimes();assertEquals(LocalTime.of(12,30),t.freeUntil());assertEquals(LocalTime.of(15,30),t.getNormalCheckin());assertEquals(LocalTime.of(18,0),t.getLateCutoff());t.setGrace(Duration.ofMinutes(45));assertEquals(LocalTime.of(12,45),t.freeUntil());}
    @Test void retentionTypeCannotBePostedWithoutFuturePolicy(){assertThrows(com.johnny.hotel.exception.BusinessException.class,()->BillingRules.item(FolioItemCommand.builder().itemType(StayItemType.EARLY_DEPARTURE_RETENTION_FEE.name()).build()));}
}
