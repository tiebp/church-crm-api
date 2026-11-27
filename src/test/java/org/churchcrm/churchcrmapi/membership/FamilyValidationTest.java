package org.churchcrm.churchcrmapi.membership;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FamilyValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void minimalRequestHasNoViolations() {
        var dto = new CreateFamilyDto("Doe", null, null);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void completeRequestHasNoViolations() {
        var address = new FamilyAddressDto("123 Main Street", "Suite 100", "Springfield", "Illinois", "United States", "62701");
        var dto = new CreateFamilyDto("Doe", address, LocalDate.of(2005, 6, 18));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void nameIsTrimmed() {
        var dto = new CreateFamilyDto("  Doe  ", null, null);

        assertThat(dto.name()).isEqualTo("Doe");
    }

    @Test
    void nameLengthIsValidatedAfterTrimming() {
        var valid = new CreateFamilyDto("  abc  ", null, null);
        var tooShort = new CreateFamilyDto("  ab  ", null, null);

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(tooShort))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void nameLongerThanTwoHundredCharactersIsRejected() {
        var dto = new CreateFamilyDto("a".repeat(201), null, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNameIsRejected(String name) {
        var dto = new CreateFamilyDto(name, null, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void nullNameIsRejected() {
        var dto = new CreateFamilyDto(null, null, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void omittedOptionalAddressFieldsAreValid() {
        var address = new FamilyAddressDto("123 Main Street", null, "Springfield", null, null, null);
        var dto = new CreateFamilyDto("Doe", address, null);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void blankOptionalAddressFieldsAreNormalizedToNullAndValid() {
        var address = new FamilyAddressDto("123 Main Street", "   ", "Springfield", "", "  ", null);
        var dto = new CreateFamilyDto("Doe", address, null);

        assertThat(address.line2()).isNull();
        assertThat(address.state()).isNull();
        assertThat(address.country()).isNull();
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void missingLine1IsRejected() {
        var address = new FamilyAddressDto(null, null, "Springfield", null, null, null);
        var dto = new CreateFamilyDto("Doe", address, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("address.line1");
    }

    @Test
    void missingCityIsRejected() {
        var address = new FamilyAddressDto("123 Main Street", null, null, null, null, null);
        var dto = new CreateFamilyDto("Doe", address, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("address.city");
    }

    @ParameterizedTest
    @ValueSource(strings = {"line2", "state", "country", "postalCode"})
    void tooShortOptionalAddressFieldIsRejected(String field) {
        var address = switch (field) {
            case "line2" -> new FamilyAddressDto("123 Main Street", "ab", "Springfield", null, null, null);
            case "state" -> new FamilyAddressDto("123 Main Street", null, "Springfield", "ab", null, null);
            case "country" -> new FamilyAddressDto("123 Main Street", null, "Springfield", null, "ab", null);
            case "postalCode" -> new FamilyAddressDto("123 Main Street", null, "Springfield", null, null, "ab");
            default -> throw new IllegalArgumentException(field);
        };
        var dto = new CreateFamilyDto("Doe", address, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("address." + field);
    }

    @Test
    void validWeddingDateIsAccepted() {
        var dto = new CreateFamilyDto("Doe", null, LocalDate.of(2005, 6, 18));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void weddingDateIsDeserializedFromIsoDate() throws Exception {
        var dto = objectMapper.readValue("""
                {"name":"Doe","weddingDate":"2005-06-18"}
                """, CreateFamilyDto.class);

        assertThat(dto.weddingDate()).isEqualTo(LocalDate.of(2005, 6, 18));
    }

    @Test
    void malformedWeddingDateIsRejected() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {"name":"Doe","weddingDate":"18/06/2005"}
                """, CreateFamilyDto.class))
                .isInstanceOf(JsonProcessingException.class);
    }
}
