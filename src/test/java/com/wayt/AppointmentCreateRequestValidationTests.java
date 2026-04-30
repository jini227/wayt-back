package com.wayt;

import com.wayt.dto.AppointmentDtos;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentCreateRequestValidationTests {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsTextFieldsAtTheirStorageLimits() {
        assertThat(violatedFields(request(
                repeated("가", 20),
                repeated("나", 200),
                repeated("다", 30),
                repeated("라", 150)
        ))).isEmpty();
    }

    @Test
    void rejectsTextFieldsBeyondTheirStorageLimitsBeforeDatabaseInsert() {
        assertThat(violatedFields(request(repeated("가", 21), "place", "penalty", "memo")))
                .contains("title");
        assertThat(violationMessages(request(repeated("가", 21), "place", "penalty", "memo")))
                .contains("약속 이름은 20자 이하로 입력해 주세요.");
        assertThat(violatedFields(request("title", repeated("나", 201), "penalty", "memo")))
                .contains("placeName");
        assertThat(violatedFields(request("title", "place", repeated("다", 31), "memo")))
                .contains("penalty");
        assertThat(violationMessages(request("title", "place", repeated("다", 31), "memo")))
                .contains("벌칙은 30자 이하로 입력해 주세요.");
        assertThat(violatedFields(request("title", "place", "penalty", repeated("라", 151))))
                .contains("memo");
        assertThat(violationMessages(request("title", "place", "penalty", repeated("라", 151))))
                .contains("메모는 150자 이하로 입력해 주세요.");
    }

    private static AppointmentDtos.AppointmentCreateRequest request(
            String title,
            String placeName,
            String penalty,
            String memo
    ) {
        return new AppointmentDtos.AppointmentCreateRequest(
                "@host",
                title,
                placeName,
                37.5665,
                126.9780,
                OffsetDateTime.now().plusHours(1),
                60,
                penalty,
                20,
                0,
                memo
        );
    }

    private static Set<String> violatedFields(AppointmentDtos.AppointmentCreateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> violationMessages(AppointmentDtos.AppointmentCreateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }

    private static String repeated(String text, int count) {
        return text.repeat(count);
    }
}
