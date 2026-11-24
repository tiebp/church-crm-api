package org.churchcrm.churchcrmapi.membership.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.enabled=false"
})
class MembershipStatusOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentContainsMembershipStatusPathsAndCreateSchema() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/membership/statuses'].post").exists())
                .andExpect(jsonPath("$.paths['/membership/statuses'].get").exists())
                .andExpect(jsonPath("$.paths['/membership/statuses/{id}'].get").exists())
                .andExpect(jsonPath("$.components.schemas.CreateMembershipStatusDto").exists());
    }
}
