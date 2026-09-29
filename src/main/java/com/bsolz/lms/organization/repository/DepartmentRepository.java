package com.bsolz.lms.organization.repository;

import com.bsolz.lms.organization.entity.Department;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

	@Query("select d from Department d where lower(d.code) in :lowerCodes")
	List<Department> findAllByLowerCodeIn(@Param("lowerCodes") Collection<String> lowerCodes);

	/** Whether {@code candidateId} is {@code departmentId} itself or one of its descendants. */
	@Query(value = """
			WITH RECURSIVE subtree AS (
			    SELECT id FROM department WHERE id = :departmentId
			    UNION
			    SELECT d.id FROM department d JOIN subtree s ON d.parent_department_id = s.id
			)
			SELECT EXISTS (SELECT 1 FROM subtree WHERE id = :candidateId)
			""", nativeQuery = true)
	boolean isInSubtree(@Param("departmentId") UUID departmentId, @Param("candidateId") UUID candidateId);

}
