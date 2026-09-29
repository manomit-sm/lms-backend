package com.bsolz.lms.leave.web.dto;

import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveDayType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** @param amount leave days drawn: 1, 0.5 or 0 */
public record LeaveDayDto(LocalDate date, LeaveDayType dayType, DaySession session, BigDecimal amount) {
}
