package com.bsolz.lms.platform.repository;

import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.shared.tenancy.SchemaMigrationState;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	boolean existsByTenantKey(String tenantKey);

	boolean existsBySubdomain(String subdomain);

	List<Tenant> findAllByStatusAndMigrationState(TenantStatus status, SchemaMigrationState migrationState);

	List<Tenant> findAllByStatusNot(TenantStatus status);

}
