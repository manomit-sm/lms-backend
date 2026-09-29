package com.bsolz.lms.leave.web;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leave.service.LeaveApplication;
import com.bsolz.lms.leave.service.LeaveAttachmentService;
import com.bsolz.lms.leave.service.LeaveFilter;
import com.bsolz.lms.leave.service.LeaveQueryService;
import com.bsolz.lms.leave.service.LeaveRequestService;
import com.bsolz.lms.leave.web.dto.AttachmentDownloadResponse;
import com.bsolz.lms.leave.web.dto.AttachmentUploadRequest;
import com.bsolz.lms.leave.web.dto.AttachmentUploadResponse;
import com.bsolz.lms.leave.web.dto.CancelLeaveRequest;
import com.bsolz.lms.leave.web.dto.LeaveApplicationRequest;
import com.bsolz.lms.leave.web.dto.LeaveRequestDetail;
import com.bsolz.lms.leave.web.dto.LeaveRequestSummary;
import com.bsolz.lms.leave.web.dto.PendingApprovalResponse;
import com.bsolz.lms.leave.web.dto.PreviewResponse;
import com.bsolz.lms.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leave requests. Applying (preview, submit, attachments) needs LEAVE_APPLY; withdrawing and cancelling
 * are for the requester (cancelling also for LEAVE_MANAGE); reading follows the rules in
 * {@link LeaveQueryService}. Approvers decide through {@code /api/v1/approvals/tasks}.
 */
@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
class LeaveRequestController {

	private final LeaveRequestService requestService;

	private final LeaveQueryService queryService;

	private final LeaveAttachmentService attachmentService;

	@PostMapping("/preview")
	@PreAuthorize("hasAuthority('LEAVE_APPLY')")
	PreviewResponse preview(@Valid @RequestBody LeaveApplicationRequest request) {
		return requestService.preview(toApplication(request));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('LEAVE_APPLY')")
	ResponseEntity<LeaveRequestDetail> submit(@Valid @RequestBody LeaveApplicationRequest request) {
		LeaveRequestDetail created = requestService.submit(toApplication(request));
		return ResponseEntity.created(URI.create("/api/v1/leave-requests/" + created.id())).body(created);
	}

	/** Defaults to everyone the caller can see; {@code from}/{@code to} select requests overlapping the range. */
	@GetMapping
	PageResponse<LeaveRequestSummary> list(@RequestParam(required = false) UUID employeeId,
			@RequestParam(required = false) LeaveStatus status, @RequestParam(required = false) UUID leaveTypeId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(queryService.list(new LeaveFilter(employeeId, status, leaveTypeId, from, to), pageable));
	}

	@GetMapping("/pending-approval")
	List<PendingApprovalResponse> pendingApproval() {
		return queryService.pendingApproval();
	}

	@GetMapping("/{requestId}")
	LeaveRequestDetail get(@PathVariable UUID requestId) {
		return queryService.get(requestId);
	}

	@PostMapping("/{requestId}/withdraw")
	LeaveRequestDetail withdraw(@PathVariable UUID requestId) {
		return requestService.withdraw(requestId);
	}

	@PostMapping("/{requestId}/cancel")
	LeaveRequestDetail cancel(@PathVariable UUID requestId, @Valid @RequestBody CancelLeaveRequest request) {
		return requestService.cancel(requestId, request.reason());
	}

	/** Step one of attaching a document: returns where to upload it. */
	@PostMapping("/attachments")
	@PreAuthorize("hasAuthority('LEAVE_APPLY')")
	AttachmentUploadResponse requestUpload(@Valid @RequestBody AttachmentUploadRequest request) {
		return attachmentService.requestUpload(request);
	}

	@GetMapping("/{requestId}/attachments/{attachmentId}/download")
	AttachmentDownloadResponse download(@PathVariable UUID requestId, @PathVariable UUID attachmentId) {
		return queryService.download(requestId, attachmentId);
	}

	private static LeaveApplication toApplication(LeaveApplicationRequest request) {
		return LeaveApplication.of(request.leaveTypeId(), request.startDate(), request.endDate(),
				request.startSession(), request.endSession(), request.reason(), request.attachmentIds());
	}

}
