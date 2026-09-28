package com.bsolz.lms.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class HalfDaysValidator implements ConstraintValidator<HalfDays, BigDecimal> {

	private static final BigDecimal HALF = new BigDecimal("0.5");

	@Override
	public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
		return value == null || isHalfDays(value);
	}

	public static boolean isHalfDays(BigDecimal value) {
		return value.remainder(HALF).signum() == 0;
	}

}
