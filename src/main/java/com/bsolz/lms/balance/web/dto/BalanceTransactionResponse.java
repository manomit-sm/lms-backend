package com.bsolz.lms.balance.web.dto;

import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A ledger entry.
 *
 * @param availableAfter the balance's available days right after this entry
 * @param createdBy the acting user's id; null for system actions
 */
public record BalanceTransactionResponse(UUID id, BalanceTransactionType type, BigDecimal amount,
		BigDecimal availableAfter, BalanceReferenceType referenceType, UUID referenceId, String note,
		Instant createdAt, UUID createdBy) {
}
