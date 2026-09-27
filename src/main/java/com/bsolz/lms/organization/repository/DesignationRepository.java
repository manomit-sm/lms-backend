package com.bsolz.lms.organization.repository;

import com.bsolz.lms.organization.entity.Designation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

}
