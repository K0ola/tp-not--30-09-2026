package com.fitconnect.classservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class ValidDurationValidator implements ConstraintValidator<ValidDuration, Integer> {

    public static final Set<Integer> ALLOWED = Set.of(30, 45, 60, 90);

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // @NotNull gere le cas null separement
        return value == null || ALLOWED.contains(value);
    }
}
