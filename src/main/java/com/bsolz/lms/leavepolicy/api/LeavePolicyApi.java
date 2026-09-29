package com.bsolz.lms.leavepolicy.api;

import com.bsolz.lms.organization.api.EmployeeSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Leave types, leave periods and policy resolution for other modules. All calls need a tenant bound. */
public interface LeavePolicyApi {

	Optional<LeaveTypeInfo> findLeaveType(UUID leaveTypeId);

	/** Active leave types in display order. */
	List<LeaveTypeInfo> findActiveLeaveTypes();

	Optional<LeavePeriodInfo> findPeriod(UUID leavePeriodId);

	Optional<LeavePeriodInfo> findPeriodContaining(LocalDate date);

	/** Open periods, oldest first. */
	List<LeavePeriodInfo> findOpenPeriods();

	/**
	 * The period containing the date, creating it first if there is none: the leave year (from the
	 * tenant's leave-year start month) containing the date, shortened where it would overlap an existing
	 * period. Joins the caller's transaction, or runs in its own.
	 */
	LeavePeriodInfo openPeriodContaining(LocalDate date);

	/** Marks the period closed: its year-end processing is done. Idempotent. */
	void closePeriod(UUID leavePeriodId);

	/**
	 * The policy for this employee and leave type on {@code asOf}: among the active policies of the (active)
	 * leave type in effect that day, the one whose best matching applicability rule is most specific.
	 * Empty means the employee is not eligible for the leave type.
	 * <p>
	 * Specificity ranks the criteria department &gt; designation &gt; location &gt; employment type &gt;
	 * gender: a rule naming a department always beats one that doesn't; among those, one that also names
	 * a designation wins, and so on.
	 */
	Optional<ResolvedPolicy> resolve(EmployeeSummary employee, UUID leaveTypeId, LocalDate asOf);

}
