package com.bsolz.lms.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A number of leave days: a whole or half day ({@code 1}, {@code 1.5}, {@code -0.5}). Null is valid. */
@Documented
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HalfDaysValidator.class)
public @interface HalfDays {

	String message() default "must be a whole or half number of days";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
