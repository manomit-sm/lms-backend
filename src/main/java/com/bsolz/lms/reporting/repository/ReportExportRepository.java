package com.bsolz.lms.reporting.repository;

import com.bsolz.lms.reporting.entity.ReportExport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {

	Optional<ReportExport> findByIdAndRequestedByUserId(UUID id, UUID requestedByUserId);

	List<ReportExport> findTop20ByRequestedByUserIdOrderByCreatedAtDesc(UUID requestedByUserId);

}
