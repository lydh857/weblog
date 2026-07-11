package com.blog.infra.captcha.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CaptchaVerifyRequestValidationTest {

    private final Validator validator;

    CaptchaVerifyRequestValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void shouldValidateNestedTrackPointCoordinates() {
        CaptchaVerifyRequest request = new CaptchaVerifyRequest();
        request.setCaptchaToken("11111111-1111-1111-1111-111111111111");
        request.setScene("login-password");
        request.setSliderPosition(120);
        request.setSlideTrack(List.of(
                new TrackPoint(0, 0, 0),
                new TrackPoint(20, 0.2, 80),
                new TrackPoint(50, -0.1, 160),
                new TrackPoint(90, 0.4, 240),
                new TrackPoint(120, 2000, 320)
        ));

        Set<ConstraintViolation<CaptchaVerifyRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }
}
