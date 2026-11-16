package org.churchcrm.churchcrmapi.organization.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import org.churchcrm.churchcrmapi.identity.UserDto;
import org.churchcrm.churchcrmapi.identity.internal.IUserService;
import org.churchcrm.churchcrmapi.organization.AddressDto;
import org.churchcrm.churchcrmapi.organization.ChurchDto;
import org.churchcrm.churchcrmapi.organization.CreateChurchDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.enabled=false"
})
@Transactional
class ChurchCreationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void createChurch_WithUniqueHostname_ShouldCreateChurch() throws Exception {
        // Given
        var dto = new CreateChurchDto("Test Church", "unique.church", "user", "email@test.com", "John Doe", null);

        // When/Then
        var response = mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    void createChurch_WithDuplicateHostname_ShouldReturnConflict() throws Exception {
        // First, create a church
        var dto1 = new CreateChurchDto("First Church", "duplicate.church", "user1", "email1@test.com", "John Doe", null);
        mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        // Now try to create another with same hostname
        var dto2 = new CreateChurchDto("Second Church", "duplicate.church", "user2", "email2@test.com", "Jane Doe", null);

        // When/Then
        mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void createChurch_WithAddress_ShouldCreateChurchWithAddress() throws Exception {
        // Given
        var address = new AddressDto("123 Main St", "Suite 100", "Springfield", "62701");
        var dto = new CreateChurchDto("Church With Address", "address.church", "user", "email@test.com", "John Doe", address);

        // When
        var response = mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        // Then
        String responseBody = response.getResponse().getContentAsString();
        ChurchDto createdChurch = objectMapper.readValue(responseBody, ChurchDto.class);
        String location = response.getResponse().getHeader("Location");
        assertThat(location).endsWith("/churches/" + createdChurch.id());
    }

    @Test
    void createChurch_WithInvalidData_ShouldReturnBadRequest() throws Exception {
        // Given - invalid data (empty name)
        var addr = new AddressDto("", "", "", "");
        var dto = new CreateChurchDto("", "invalid.church", "user", "invalid-email", "John Doe", addr);

        // When/Then
        mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void createChurch_WithInvalidAddress_ShouldReturnBadRequest() throws Exception {
        // Given - valid church data but invalid address (missing required city)
        var invalidAddress = new AddressDto("123 Main St", null, "", "12345"); // empty city
        var dto = new CreateChurchDto("Valid Church", "valid.church", "user", "email@test.com", "John Doe", invalidAddress);

        // When/Then
        mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createChurch_ShouldCreateAdminUser_WhenChurchCreated() throws Exception {
        // Given
        var faker = new Faker();
        var hostName = "admin-" + UUID.randomUUID().toString().substring(0, 8) + ".church";
        var username = faker.internet().emailAddress();
        var fullName = faker.name().fullName();
        var dto = new CreateChurchDto("Admin Test Church", hostName, username, username, fullName, null);

        // When
        var response = mockMvc.perform(post("/churches")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        // Then - ChurchCreated is handled asynchronously after the transaction commits
        ChurchDto createdChurch = objectMapper.readValue(
                response.getResponse().getContentAsString(), ChurchDto.class);
        UserDto adminUser = awaitUser(username);

        assertThat(adminUser).isNotNull();
        assertThat(adminUser.username()).isEqualTo(username);
        assertThat(adminUser.fullname()).isEqualTo(fullName);
        assertThat(adminUser.email()).isEqualTo(username);
        assertThat(adminUser.churchId()).isEqualTo(createdChurch.id());
    }

    private UserDto awaitUser(String email) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            var user = userService.getByEmail(email);
            if (user != null) {
                return user;
            }
            Thread.sleep(200);
        }
        return null;
    }
}