package com.bsolz.lms.organization.repository;

import com.bsolz.lms.organization.entity.Employee;
import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

	@Override
	@EntityGraph(attributePaths = { "department", "designation", "location", "reportingManager" })
	Page<Employee> findAll(Specification<Employee> specification, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "designation", "location", "reportingManager" })
	Optional<Employee> findWithDetailsById(UUID id);

	@EntityGraph(attributePaths = { "department", "designation", "location", "reportingManager" })
	List<Employee> findAllByReportingManagerIdOrderByFirstNameAscLastNameAsc(UUID managerId);

	boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

	boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, UUID id);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

	boolean existsByReportingManagerIdAndEmploymentStatusNot(UUID managerId, EmploymentStatus status);

	/**
	 * Direct and indirect reports. {@code UNION} (not {@code UNION ALL}) also stops the recursion if
	 * bad data ever contained a cycle.
	 */
	@Query(value = """
			WITH RECURSIVE reports AS (
			    SELECT id FROM employee WHERE reporting_manager_id = :managerId
			    UNION
			    SELECT e.id FROM employee e JOIN reports r ON e.reporting_manager_id = r.id
			)
			SELECT id FROM reports
			""", nativeQuery = true)
	List<UUID> findReportingLineIds(@Param("managerId") UUID managerId);

	/** Managers above the employee, nearest first (depth-capped as a guard against bad data). */
	@Query(value = """
			WITH RECURSIVE chain (id, manager_id, depth) AS (
			    SELECT id, reporting_manager_id, 0 FROM employee WHERE id = :employeeId
			    UNION ALL
			    SELECT e.id, e.reporting_manager_id, c.depth + 1
			    FROM employee e JOIN chain c ON e.id = c.manager_id
			    WHERE c.depth < 50
			)
			SELECT id FROM chain WHERE depth > 0 ORDER BY depth
			""", nativeQuery = true)
	List<UUID> findManagerChainIds(@Param("employeeId") UUID employeeId);

	@Query(value = """
			WITH RECURSIVE reports AS (
			    SELECT id FROM employee WHERE reporting_manager_id = :managerId
			    UNION
			    SELECT e.id FROM employee e JOIN reports r ON e.reporting_manager_id = r.id
			)
			SELECT EXISTS (SELECT 1 FROM reports WHERE id = :employeeId)
			""", nativeQuery = true)
	boolean isInReportingLine(@Param("managerId") UUID managerId, @Param("employeeId") UUID employeeId);

}
