package com.johnny.hotel.wallet;

import com.johnny.hotel.guest.GuestMapper;
import com.johnny.hotel.guest.GuestRequests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TargetedBoundaryDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired GuestMapper guestMapper;

    @Test void productionRoutesHaveNoRoleProbeOrManualBookedOccupied() throws Exception {
        mvc.perform(get("/api/test/roles/customer").with(authentication(auth("CUSTOMER"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/rooms/{id}/booked",room1).with(authentication(auth("STAFF"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/rooms/{id}/occupied",room1).with(authentication(auth("STAFF"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/rooms/{id}/maintenance",room1).with(authentication(auth("STAFF"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/rooms/{id}/available",room1).with(authentication(auth("STAFF"))))
                .andExpect(status().isOk());
    }

    @Test void financeGuestReadOmitsUnneededIdentityFields() throws Exception {
        long finance=users.registerEmployee(employeeRequest("FINANCE")).getId();created.add(finance);actors.put("FINANCE",finance);
        jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",finance);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='FINANCE'",finance);
        guests.saveMe(uid("CUSTOMER"),GuestRequests.Profile.builder().firstName("Finance").lastName("Guest")
                .phone("555123456").email(run+"@example.test").documentNumber("SECRET1234").build());
        String body=mvc.perform(get("/api/finance/operations/guests").with(authentication(auth("FINANCE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("Finance"));
        for(String field:new String[]{"phone","email","date_of_birth","document_number","document_expiry_date"})
            assertFalse(body.contains("\""+field+"\""),field);
        assertFalse(body.contains("SECRET1234"));
    }

    @Test void invalidClientCodeIsBadRequest() throws Exception {
        mvc.perform(get("/api/stays").param("status","999").with(authentication(auth("CUSTOMER"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/repair-orders").param("status","999").with(authentication(auth("STAFF"))))
                .andExpect(status().isBadRequest());
    }

    @Test void profileUpdateWaitsForExistingRowLock() throws Exception {
        var profile=GuestRequests.Profile.builder().firstName("Initial").lastName("Guest").build();
        long id=guests.saveMe(uid("CUSTOMER"),profile).id();
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var holder=pool.submit(()->new TransactionTemplate(txManager).execute(x->{
                assertNotNull(guestMapper.lockProfile(id));locked.countDown();
                try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}return null;}));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            var update=pool.submit(()->guests.saveMe(uid("CUSTOMER"),GuestRequests.Profile.builder().firstName("Updated").lastName("Guest").build()));
            Thread.sleep(150);
            assertFalse(update.isDone(),"Profile update must wait for the row lock");
            release.countDown();holder.get(10,TimeUnit.SECONDS);
            assertEquals("Updated",update.get(10,TimeUnit.SECONDS).firstName());
        } finally {release.countDown();pool.shutdownNow();}
    }
}
