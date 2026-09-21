package com.johnny.hotel.wallet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserDirectoryBoundaryTest extends WalletDevelopmentFixture {
    @Test void customerCanReadSelfButNotAnotherCustomer() throws Exception {
        mvc.perform(get("/api/users/{id}",uid("CUSTOMER")).with(authentication(auth("CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(uid("CUSTOMER")))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/users/{id}",uid("OTHER_CUSTOMER")).with(authentication(auth("CUSTOMER"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/users/{id}",Long.MAX_VALUE).with(authentication(auth("CUSTOMER"))))
                .andExpect(status().isNotFound());
    }

    @Test void authorizedEmployeeReceivesOnlyPublicProjection() throws Exception {
        String hash=jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,uid("OTHER_CUSTOMER"));
        String body=mvc.perform(get("/api/users/{id}",uid("OTHER_CUSTOMER")).with(authentication(auth("STAFF"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(uid("OTHER_CUSTOMER")))
                .andExpect(jsonPath("$.data.password").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(hash));
        mvc.perform(get("/api/users/{id}",uid("MANAGER")).with(authentication(auth("STAFF"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}",uid("MANAGER")).with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/users/{id}",uid("OTHER_CUSTOMER")).with(authentication(auth("HR_ADMIN"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}",uid("OTHER_CUSTOMER")))
                .andExpect(status().isUnauthorized());
    }
}
