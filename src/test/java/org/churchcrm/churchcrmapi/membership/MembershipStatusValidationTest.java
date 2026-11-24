package org.churchcrm.churchcrmapi.membership;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipStatusValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

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
    void validRequestHasNoViolations() {
        var dto = new CreateMembershipStatusDto("member", "Member", "Confirmed church member", 10, false);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void omittedOptionalFieldsAreValid() {
        var dto = new CreateMembershipStatusDto("member", "Member", null, null, null);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Member", "1member", "member-status", "member status", ""})
    void invalidCodeIsRejected(String code) {
        var dto = new CreateMembershipStatusDto(code, "Member", null, 0, false);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("code");
    }

    @Test
    void codeLongerThanFiftyCharactersIsRejected() {
        var dto = new CreateMembershipStatusDto("a".repeat(51), "Member", null, 0, false);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("code");
    }

    @Test
    void blankLabelIsRejected() {
        var dto = new CreateMembershipStatusDto("member", "   ", null, 0, false);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("label");
    }

    @Test
    void descriptionLongerThanFiveHundredCharactersIsRejected() {
        var dto = new CreateMembershipStatusDto("member", "Member", "a".repeat(501), 0, false);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description");
    }

    @Test
    void negativeSortOrderIsRejected() {
        var dto = new CreateMembershipStatusDto("member", "Member", null, -1, false);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sortOrder");
    }
}
