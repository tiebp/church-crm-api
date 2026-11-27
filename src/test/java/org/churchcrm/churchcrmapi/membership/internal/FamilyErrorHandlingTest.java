package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.utils.JwtTestDsl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.enabled=true"
})
class FamilyErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FamilyService service;

    @Test
    void unexpectedFailureReturnsGenericProblemDetail() throws Exception {
        when(service.create(any(CreateFamilyDto.class), any(UUID.class)))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        mockMvc.perform(post("/membership/families")
                        .with(JwtTestDsl.jwt().organization(UUID.randomUUID()).build())
                        .contentType("application/json")
                        .content("{\"name\":\"Doe\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail").value(not(containsString("sensitive internal detail"))));
    }
}
