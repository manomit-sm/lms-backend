package com.bsolz.lms.organization.web.dto;

import java.util.UUID;

/** A related record's id and display name. */
public record Reference(UUID id, String name) {
}
