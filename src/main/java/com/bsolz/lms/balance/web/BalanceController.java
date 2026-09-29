package com.bsolz.lms.balance.web;

import com.bsolz.lms.balance.service.AllocationService;
import com.bsolz.lms.balance.service.BalanceQueryService;
import com.bsolz.lms.balance.web.dto.AdjustmentRequest;
import com.bsolz.lms.balance.web.dto.AllocationRequest;
import com.bsolz.lms.balance.web.dto.AllocationResult;
import com.bsolz.lms.balance.web.dto.BalanceResponse;
import com.bsolz.lms.balance.web.dto.BalanceTransactionResponse;
import com.bsolz.lms.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leave balances. Reads follow the employee data scope (self / reporting line / everyone); adjustments
 * and bulk allocation need BALANCE_ADJUST.
 */
@RestController
@RequestMapping("/api/v1/balances")
@RequiredArgsConstructor
class BalanceController {

	private final BalanceQueryService queryService;

	private final AllocationService allocationService;

	/** Defaults: the current user, the current leave period. */
	@GetMapping
	List<BalanceResponse> list(@RequestParam(required = false) UUID employeeId,
			@RequestParam(required = false) UUID leavePeriodId) {
		return queryService.list(employeeId, leavePeriodId);
	}

	@GetMapping("/{balanceId}/transactions")
	PageResponse<BalanceTransactionResponse> transactions(@PathVariable UUID balanceId,
			@PageableDefault(size = 50) Pageable pageable) {
		return PageResponse.from(queryService.transactions(balanceId, pageable));
	}

	@PostMapping("/adjustments")
	@PreAuthorize("hasAuthority('BALANCE_ADJUST')")
	BalanceResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
		return queryService.adjust(request);
	}

	/** Allocates a period's entitlement to every current employee who hasn't had it yet. */
	@PostMapping("/allocations")
	@PreAuthorize("hasAuthority('BALANCE_ADJUST')")
	AllocationResult allocate(@RequestBody AllocationRequest request) {
		return allocationService.allocatePeriod(request.leavePeriodId(), request.leaveTypeIds());
	}

}
