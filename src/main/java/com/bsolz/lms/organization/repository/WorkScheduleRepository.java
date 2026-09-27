package com.bsolz.lms.organization.repository;

import com.bsolz.lms.organization.entity.WorkSchedule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

	Optional<WorkSchedule> findByDefaultScheduleTrue();

}
