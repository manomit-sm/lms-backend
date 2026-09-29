package com.bsolz.lms.identity.repository;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.model.enums.UserStatus;
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

public interface AppUserRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<AppUser> findByIdpSubject(String idpSubject);

	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<AppUser> findWithRolesById(UUID id);

	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<AppUser> findByEmailIgnoreCase(String email);

	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<AppUser> findByEmployeeId(UUID employeeId);

	@Override
	@EntityGraph(attributePaths = "roles")
	Page<AppUser> findAll(Specification<AppUser> specification, Pageable pageable);

	@Query("select count(distinct u) from AppUser u join u.roles r where r.code = :roleCode and u.status <> :excluded")
	long countWithRoleAndStatusNot(@Param("roleCode") String roleCode, @Param("excluded") UserStatus excluded);

	boolean existsByRolesId(UUID roleId);

	@Query("select u.id from AppUser u where u.employeeId = :employeeId and u.status <> :excluded")
	Optional<UUID> findIdByEmployeeIdAndStatusNot(@Param("employeeId") UUID employeeId,
			@Param("excluded") UserStatus excluded);

	@Query("select distinct u.id from AppUser u join u.roles r where r.code = :roleCode and u.status <> :excluded")
	List<UUID> findIdsWithRoleAndStatusNot(@Param("roleCode") String roleCode, @Param("excluded") UserStatus excluded);

}
