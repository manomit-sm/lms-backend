package com.bsolz.lms.leave.entity;

import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveDayType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One calendar day of a request.
 *
 * @param amount the leave days it draws: 1 or 0.5 on a working day; 0 on a weekend or holiday unless
 * the sandwich rule counts it (then 1)
 */
@Embeddable
public record LeaveDay(@Column(name = "leave_date") LocalDate date, @Enumerated(EnumType.STRING) LeaveDayType dayType,
		@Enumerated(EnumType.STRING) DaySession session, BigDecimal amount) {
}
