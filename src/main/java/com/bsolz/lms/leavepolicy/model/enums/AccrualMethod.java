package com.bsolz.lms.leavepolicy.model.enums;

/** How a policy's entitlement for a leave period is credited. */
public enum AccrualMethod {

	/** The whole entitlement at the start of the period (prorated for joiners). */
	UPFRONT,

	/** One twelfth per month, by the accrual job. */
	MONTHLY,

	/** One quarter per quarter, by the accrual job. */
	QUARTERLY

}
