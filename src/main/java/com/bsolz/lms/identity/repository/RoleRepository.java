package com.bsolz.lms.identity.repository;

import com.bsolz.lms.identity.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

	@EntityGraph(attributePaths = "permissions")
	Optional<Role> findByCode(String code);

	@EntityGraph(attributePaths = "permissions")
	Optional<Role> findWithPermissionsById(UUID id);

	@EntityGraph(attributePaths = "permissions")
	List<Role> findAllByCodeIn(Collection<String> codes);

	@EntityGraph(attributePaths = "permissions")
	List<Role> findAllByOrderBySystemRoleDescNameAsc();

	boolean existsByCode(String code);

}
