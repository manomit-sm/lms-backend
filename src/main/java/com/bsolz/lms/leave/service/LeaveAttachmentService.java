package com.bsolz.lms.leave.service;

import com.bsolz.lms.leave.entity.LeaveAttachment;
import com.bsolz.lms.leave.exception.LeaveErrorCode;
import com.bsolz.lms.leave.repository.LeaveAttachmentRepository;
import com.bsolz.lms.leave.web.dto.AttachmentDownloadResponse;
import com.bsolz.lms.leave.web.dto.AttachmentUploadRequest;
import com.bsolz.lms.leave.web.dto.AttachmentUploadResponse;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.storage.FileStorage;
import com.bsolz.lms.shared.storage.PresignedDownload;
import com.bsolz.lms.shared.storage.PresignedUpload;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supporting documents. The client asks for an upload URL, uploads straight to storage, then names the
 * attachment when submitting. Keys are prefixed with the tenant id, so one tenant's files never share a
 * path with another's.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveAttachmentService {

	static final Set<String> CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

	private final LeaveAttachmentRepository repository;

	private final FileStorage storage;

	public AttachmentUploadResponse requestUpload(AttachmentUploadRequest request) {
		String contentType = request.contentType().toLowerCase(Locale.ROOT);
		if (!CONTENT_TYPES.contains(contentType)) {
			throw new ApiException(LeaveErrorCode.UNSUPPORTED_ATTACHMENT, "Attach a PDF, JPEG or PNG file");
		}
		String key = "tenants/" + TenantContext.require().id() + "/leave-attachments/" + UUID.randomUUID() + "/"
				+ safeFileName(request.fileName());
		LeaveAttachment attachment = repository.save(new LeaveAttachment(CurrentUser.require().userId(),
				request.fileName().trim(), contentType, request.sizeBytes(), key));
		PresignedUpload upload = storage.presignUpload(key, contentType, request.sizeBytes());
		return new AttachmentUploadResponse(attachment.getId(), upload.url(), upload.method(), upload.headers(),
				upload.expiresAt());
	}

	/** The user's uploaded, not yet used attachments with these ids. */
	List<LeaveAttachment> requireUnused(List<UUID> attachmentIds, UUID userId) {
		List<LeaveAttachment> attachments = repository.findAllById(attachmentIds);
		if (attachments.size() != Set.copyOf(attachmentIds).size() || attachments.stream().anyMatch(
				attachment -> !attachment.getUploadedByUserId().equals(userId) || attachment.getLeaveRequestId() != null)) {
			throw new ApiException(LeaveErrorCode.ATTACHMENT_NOT_FOUND, "Attachment not found");
		}
		attachments.stream().filter(attachment -> !storage.exists(attachment.getStorageKey())).findFirst()
				.ifPresent(missing -> {
					throw new ApiException(LeaveErrorCode.ATTACHMENT_NOT_UPLOADED,
							"'" + missing.getFileName() + "' hasn't been uploaded yet");
				});
		return attachments;
	}

	/** Callers check the user may see the request. */
	@Transactional(readOnly = true)
	AttachmentDownloadResponse download(UUID leaveRequestId, UUID attachmentId) {
		LeaveAttachment attachment = repository.findByIdAndLeaveRequestId(attachmentId, leaveRequestId)
				.orElseThrow(() -> new ApiException(LeaveErrorCode.ATTACHMENT_NOT_FOUND, "Attachment not found"));
		PresignedDownload download = storage.presignDownload(attachment.getStorageKey(), attachment.getFileName());
		return new AttachmentDownloadResponse(download.url(), download.expiresAt());
	}

	private static String safeFileName(String fileName) {
		String safe = fileName.trim().replaceAll("[^A-Za-z0-9._-]", "_");
		return safe.length() > 100 ? safe.substring(safe.length() - 100) : safe;
	}

}
