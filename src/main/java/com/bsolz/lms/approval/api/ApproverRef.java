package com.bsolz.lms.approval.api;

import java.util.UUID;

/** @param name the employee's name, or the user's email if they have no employee record */
public record ApproverRef(UUID userId, String name) {
}
