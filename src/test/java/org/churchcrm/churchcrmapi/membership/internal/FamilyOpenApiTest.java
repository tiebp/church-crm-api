package org.churchcrm.churchcrmapi.membership.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.enabled=false"
})
class FamilyOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentContainsOnlyFamilyCreation() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/membership/families'].post").exists())
                .andExpect(jsonPath("$.paths['/membership/families'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/membership/families'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/membership/families'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/membership/families'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/membership/families/{id}']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateFamilyDto").exists())
                .andExpect(jsonPath("$.components.schemas.FamilyDto").exists());
    }

    @Test
    void noFamilyReadRouteIsDocumentedOrServed() throws Exception {
        var result = mockMvc.perform(get("/membership/families/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Doe");
    }
}
