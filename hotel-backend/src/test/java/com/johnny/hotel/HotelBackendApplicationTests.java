package com.johnny.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests", matches="true")
class HotelBackendApplicationTests extends com.johnny.hotel.support.IsolatedMysqlTest {

    @Test
    void contextLoads() {
    }

}
