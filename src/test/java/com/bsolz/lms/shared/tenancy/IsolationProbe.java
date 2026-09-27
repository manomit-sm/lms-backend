package com.bsolz.lms.shared.tenancy;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Test-only entity; its table is created in each test tenant's schema by {@link TenantIsolationTests}. */
@Getter
@Entity
@Table(name = "isolation_probe")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class IsolationProbe {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String label;

	IsolationProbe(String label) {
		this.label = label;
	}

}
