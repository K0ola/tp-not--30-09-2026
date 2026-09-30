package com.fitconnect.classservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La duree d'un cours doit valoir 30, 45, 60 ou 90 minutes.
 */
@Documented
@Constraint(validatedBy = ValidDurationValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDuration {
    String message() default "La duree doit etre 30, 45, 60 ou 90 minutes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
