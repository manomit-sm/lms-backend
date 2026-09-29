package com.bsolz.lms.approval.model.enums;

/** Who approves a workflow step, resolved against the requester when an approval starts. */
public enum ApproverType {

	REPORTING_MANAGER,

	/** The reporting manager's manager. */
	SKIP_LEVEL_MANAGER,

	/** Every enabled user holding the step's role; any one of them decides. */
	ROLE,

	/** A named employee. */
	EMPLOYEE

}
