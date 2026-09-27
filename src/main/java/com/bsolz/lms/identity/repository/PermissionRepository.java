package com.bsolz.lms.identity.repository;

import com.bsolz.lms.identity.entity.Permission;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

	List<Permission> findAllByCodeIn(Collection<String> codes);

	List<Permission> findAllByOrderByModuleAscCodeAsc();

}
