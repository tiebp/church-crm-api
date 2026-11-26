package org.churchcrm.churchcrmapi.membership.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.churchcrm.churchcrmapi.membership.CreateMembershipStatusDto;
import org.churchcrm.churchcrmapi.membership.MembershipStatusDto;
import org.churchcrm.churchcrmapi.utils.JwtTestDsl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.enabled=true"
})
@Transactional
class MembershipStatusControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MembershipStatusRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void createWithValidDataReturnsCreatedStatus() throws Exception {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("member", "Member", "Confirmed member", 10, false);

        var response = mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.churchId").value(churchId.toString()))
                .andExpect(jsonPath("$.code").value("member"))
                .andExpect(jsonPath("$.label").value("Member"))
                .andExpect(jsonPath("$.description").value("Confirmed member"))
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdBy").value("user"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("user"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        MembershipStatusDto created = objectMapper.readValue(
                response.getResponse().getContentAsString(), MembershipStatusDto.class);
        assertThat(response.getResponse().getHeader("Location"))
                .endsWith("/membership/statuses/" + created.id());
    }

    @Test
    void createAppliesDefaultsWhenOptionalFieldsAreOmitted() throws Exception {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("member", "Member", null, null, null);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createWithExplicitDefaultReturnsCreatedStatus() throws Exception {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("guest", "Guest", null, 30, true);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void createRejectsInvalidCode() throws Exception {
        var dto = new CreateMembershipStatusDto("Member", "Member", null, 0, false);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    void createRejectsBlankLabel() throws Exception {
        var dto = new CreateMembershipStatusDto("member", "   ", null, 0, false);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.label").exists());
    }

    @Test
    void createRejectsLongDescription() throws Exception {
        var dto = new CreateMembershipStatusDto("member", "Member", "a".repeat(501), 0, false);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void createRejectsNegativeSortOrder() throws Exception {
        var dto = new CreateMembershipStatusDto("member", "Member", null, -1, false);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sortOrder").exists());
    }

    @Test
    void createRejectsDuplicateCodeInSameChurch() throws Exception {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("member", "Member", null, 0, false);
        create(churchId, dto);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictType").value("code"));
    }

    @Test
    void createAllowsSameCodeInAnotherChurch() throws Exception {
        var dto = new CreateMembershipStatusDto("member", "Member", null, 0, false);
        create(UUID.randomUUID(), dto);

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectsSecondDefault() throws Exception {
        UUID churchId = UUID.randomUUID();
        create(churchId, new CreateMembershipStatusDto("guest", "Guest", null, 30, true));

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateMembershipStatusDto("custom_default", "Custom Default", null, 40, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictType").value("isDefault"));
    }

    @Test
    void createIgnoresChurchIdInRequestBody() throws Exception {
        UUID churchId = UUID.randomUUID();
        UUID otherChurchId = UUID.randomUUID();
        var body = Map.of(
                "churchId", otherChurchId.toString(),
                "code", "member",
                "label", "Member",
                "sortOrder", 10,
                "isDefault", false
        );

        mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.churchId").value(churchId.toString()));
    }

    @Test
    void createWithoutJwtReturnsUnauthorized() throws Exception {
        var dto = new CreateMembershipStatusDto("member", "Member", null, 0, false);

        mockMvc.perform(post("/membership/statuses")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutAdminRoleReturnsForbidden() throws Exception {
        var dto = new CreateMembershipStatusDto("member", "Member", null, 0, false);

        mockMvc.perform(post("/membership/statuses")
                        .with(JwtTestDsl.jwt().organization(UUID.randomUUID()).build())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturnsStatusesOrderedBySortOrderIncludingInactive() throws Exception {
        UUID churchId = UUID.randomUUID();
        create(churchId, new CreateMembershipStatusDto("late", "Late", null, 30, false));
        create(churchId, new CreateMembershipStatusDto("early", "Early", null, 10, false));
        create(churchId, new CreateMembershipStatusDto("middle", "Middle", null, 20, false));
        saveInactive(churchId, "inactive");

        mockMvc.perform(get("/membership/statuses")
                        .with(admin(churchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("early", "middle", "late", "inactive")))
                .andExpect(jsonPath("$[*].code").value(hasItem("inactive")));
    }

    @Test
    void getReturnsStatusFromCallersChurch() throws Exception {
        UUID churchId = UUID.randomUUID();
        UUID id = create(churchId, new CreateMembershipStatusDto("member", "Member", null, 10, false));

        mockMvc.perform(get("/membership/statuses/" + id)
                        .with(admin(churchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("member"));
    }

    @Test
    void getReturnsNotFoundForStatusInAnotherChurch() throws Exception {
        UUID id = create(UUID.randomUUID(), new CreateMembershipStatusDto("member", "Member", null, 10, false));

        mockMvc.perform(get("/membership/statuses/" + id)
                        .with(admin(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReturnsNotFoundForMissingStatus() throws Exception {
        mockMvc.perform(get("/membership/statuses/" + UUID.randomUUID())
                        .with(admin(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID churchId) {
        return JwtTestDsl.jwt().organization(churchId).admin().build();
    }

    private UUID create(UUID churchId, CreateMembershipStatusDto dto) throws Exception {
        var response = mockMvc.perform(post("/membership/statuses")
                        .with(admin(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                response.getResponse().getContentAsString(), MembershipStatusDto.class).id();
    }

    private void saveInactive(UUID churchId, String code) {
        var status = new MembershipStatus();
        status.setChurchId(churchId);
        status.setCode(code);
        status.setLabel(code);
        status.setSortOrder(40);
        status.setIsDefault(false);
        status.setActive(false);
        repository.saveAndFlush(status);
    }
}
