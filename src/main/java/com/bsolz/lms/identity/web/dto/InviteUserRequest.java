package com.bsolz.lms.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Invites a user who isn't (yet) an employee, e.g. an additional administrator. */
public record InviteUserRequest(@NotBlank @Email @Size(max = 254) String email, @NotEmpty Set<String> roleCodes) {
}
