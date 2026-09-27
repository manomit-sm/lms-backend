package com.bsolz.lms.identity.model.enums;

/** INVITED until the first successful sign-in; DISABLED users are rejected on every request. */
public enum UserStatus {

	INVITED,
	ACTIVE,
	DISABLED

}
