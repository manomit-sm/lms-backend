package com.bsolz.lms.identity.web;

import com.bsolz.lms.identity.service.MeService;
import com.bsolz.lms.identity.web.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in itself happens at the identity provider; this is the API's view of the signed-in user. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

	private final MeService meService;

	@GetMapping("/me")
	MeResponse me() {
		return meService.me();
	}

}
