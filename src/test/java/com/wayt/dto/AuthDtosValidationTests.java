package com.wayt.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtosValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void profileNicknameAllowsSixCharacters() {
        var request = new AuthDtos.ProfileUpdateRequest("123456", null, null, null);

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void profileNicknameRejectsMoreThanSixCharacters() {
        var request = new AuthDtos.ProfileUpdateRequest("1234567", null, null, null);

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("nickname"));
    }
}
