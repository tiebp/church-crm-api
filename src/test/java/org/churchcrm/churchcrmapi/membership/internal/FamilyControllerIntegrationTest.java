package org.churchcrm.churchcrmapi.membership.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.membership.FamilyAddressDto;
import org.churchcrm.churchcrmapi.membership.FamilyDto;
import org.churchcrm.churchcrmapi.utils.JwtTestDsl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.enabled=true"
})
@Transactional
class FamilyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void createWithOnlyNameReturnsCreatedFamily() throws Exception {
        UUID churchId = UUID.randomUUID();

        var response = mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.churchId").value(churchId.toString()))
                .andExpect(jsonPath("$.name").value("Doe"))
                .andExpect(jsonPath("$.address").value(nullValue()))
                .andExpect(jsonPath("$.weddingDate").value(nullValue()))
                .andExpect(jsonPath("$.createdBy").value("user"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("user"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        FamilyDto created = objectMapper.readValue(
                response.getResponse().getContentAsString(), FamilyDto.class);
        assertThat(response.getResponse().getHeader("Location"))
                .endsWith("/membership/families/" + created.id());
    }

    @Test
    void createWithFullAddressAndWeddingDateReturnsStoredValues() throws Exception {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateFamilyDto(
                "Doe",
                new FamilyAddressDto("123 Main Street", "Suite 100", "Springfield", "Illinois", "United States", "62701"),
                LocalDate.of(2005, 6, 18));

        mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Doe"))
                .andExpect(jsonPath("$.address.line1").value("123 Main Street"))
                .andExpect(jsonPath("$.address.line2").value("Suite 100"))
                .andExpect(jsonPath("$.address.city").value("Springfield"))
                .andExpect(jsonPath("$.address.state").value("Illinois"))
                .andExpect(jsonPath("$.address.country").value("United States"))
                .andExpect(jsonPath("$.address.postalCode").value("62701"))
                .andExpect(jsonPath("$.weddingDate").value("2005-06-18"));
    }

    @Test
    void createWithRoleLessJwtReturnsCreated() throws Exception {
        UUID churchId = UUID.randomUUID();

        mockMvc.perform(post("/membership/families")
                        .with(JwtTestDsl.jwt().organization(churchId).build())
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.churchId").value(churchId.toString()));
    }

    @Test
    void createTrimsName() throws Exception {
        UUID churchId = UUID.randomUUID();

        mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content("""
                                {"name":"  Doe  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Doe"));
    }

    @Test
    void createWithEmptyOptionalAddressFieldsStoresThemAsNull() throws Exception {
        UUID churchId = UUID.randomUUID();

        mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Doe",
                                  "address":{
                                    "line1":"123 Main Street",
                                    "line2":"",
                                    "city":"Springfield",
                                    "state":"   ",
                                    "country":"",
                                    "postalCode":""
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.line1").value("123 Main Street"))
                .andExpect(jsonPath("$.address.line2").value(nullValue()))
                .andExpect(jsonPath("$.address.state").value(nullValue()))
                .andExpect(jsonPath("$.address.country").value(nullValue()))
                .andExpect(jsonPath("$.address.postalCode").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ab"})
    void createRejectsInvalidName(String name) throws Exception {
        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateFamilyDto(name, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createRejectsNameLongerThanTwoHundredCharacters() throws Exception {
        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateFamilyDto("a".repeat(201), null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createRejectsAddressWithoutLine1() throws Exception {
        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe","address":{"city":"Springfield"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.line1']").exists());
    }

    @Test
    void createRejectsAddressWithoutCity() throws Exception {
        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe","address":{"line1":"123 Main Street"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.city']").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"line2", "state", "country", "postalCode"})
    void createRejectsTooShortOptionalAddressField(String field) throws Exception {
        String body = """
                {"name":"Doe","address":{"line1":"123 Main Street","city":"Springfield","%s":"ab"}}
                """.formatted(field);

        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.%s']".formatted(field)).exists());
    }

    @Test
    void createRejectsMalformedWeddingDate() throws Exception {
        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe","weddingDate":"18/06/2005"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/membership/families")
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRejectsDuplicateNameCaseInsensitivelyInSameChurch() throws Exception {
        UUID churchId = UUID.randomUUID();
        create(churchId, """
                {"name":"Doe"}
                """);

        mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content("""
                                {"name":"doe"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictType").value("name"));

        assertThat(familyCount(churchId)).isEqualTo(1);
    }

    @Test
    void createAllowsSameNameInAnotherChurch() throws Exception {
        create(UUID.randomUUID(), """
                {"name":"Doe"}
                """);

        mockMvc.perform(post("/membership/families")
                        .with(user(UUID.randomUUID()))
                        .contentType("application/json")
                        .content("""
                                {"name":"Doe"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void createIgnoresChurchIdInRequestBody() throws Exception {
        UUID churchId = UUID.randomUUID();
        UUID otherChurchId = UUID.randomUUID();
        String body = """
                {"churchId":"%s","name":"Doe"}
                """.formatted(otherChurchId);

        var response = mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.churchId").value(churchId.toString()))
                .andReturn();

        FamilyDto created = objectMapper.readValue(
                response.getResponse().getContentAsString(), FamilyDto.class);
        UUID persistedChurchId = jdbcTemplate.queryForObject(
                "SELECT church_id FROM membership.families WHERE id = ?", UUID.class, created.id());
        assertThat(persistedChurchId).isEqualTo(churchId);
    }

    @Test
    void validationFailurePersistsNothing() throws Exception {
        UUID churchId = UUID.randomUUID();

        mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content("""
                                {"name":"ab"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(familyCount(churchId)).isZero();
    }

    @Test
    void duplicateNamePersistsNothing() throws Exception {
        UUID churchId = UUID.randomUUID();
        create(churchId, """
                {"name":"Doe"}
                """);

        mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content("""
                                {"name":"DOE"}
                                """))
                .andExpect(status().isConflict());

        assertThat(familyCount(churchId)).isEqualTo(1);
    }

    @Test
    void getFamilyRouteReturnsNoFamilyDataAndIsNotForbidden() throws Exception {
        UUID churchId = UUID.randomUUID();
        UUID id = create(churchId, """
                {"name":"Doe"}
                """);

        mockMvc.perform(get("/membership/families/" + id)
                        .with(user(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Doe"))));
    }

    private RequestPostProcessor user(UUID churchId) {
        return JwtTestDsl.jwt().organization(churchId).build();
    }

    private UUID create(UUID churchId, String body) throws Exception {
        var response = mockMvc.perform(post("/membership/families")
                        .with(user(churchId))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                response.getResponse().getContentAsString(), FamilyDto.class).id();
    }

    private long familyCount(UUID churchId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM membership.families WHERE church_id = ?", Long.class, churchId);
        return count == null ? 0 : count;
    }
}
