package com.bsolz.lms.identity.web;

import com.bsolz.lms.identity.model.enums.UserStatus;
import com.bsolz.lms.identity.service.UserAdminService;
import com.bsolz.lms.identity.web.dto.InviteUserRequest;
import com.bsolz.lms.identity.web.dto.UpdateUserRolesRequest;
import com.bsolz.lms.identity.web.dto.UserResponse;
import com.bsolz.lms.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@RequiredArgsConstructor
class UserController {

	private final UserAdminService userAdminService;

	@GetMapping
	PageResponse<UserResponse> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) UserStatus status,
			@PageableDefault(size = 20, sort = "email") Pageable pageable) {
		return PageResponse.from(userAdminService.list(search, status, pageable));
	}

	@GetMapping("/{userId}")
	UserResponse get(@PathVariable UUID userId) {
		return userAdminService.get(userId);
	}

	@PostMapping
	ResponseEntity<UserResponse> invite(@Valid @RequestBody InviteUserRequest request) {
		UserResponse invited = userAdminService.invite(request);
		return ResponseEntity.created(URI.create("/api/v1/users/" + invited.id())).body(invited);
	}

	@PutMapping("/{userId}/roles")
	UserResponse updateRoles(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRolesRequest request) {
		return userAdminService.updateRoles(userId, request.roleCodes());
	}

	@PostMapping("/{userId}/disable")
	UserResponse disable(@PathVariable UUID userId) {
		return userAdminService.disable(userId);
	}

	@PostMapping("/{userId}/enable")
	UserResponse enable(@PathVariable UUID userId) {
		return userAdminService.enable(userId);
	}

}
