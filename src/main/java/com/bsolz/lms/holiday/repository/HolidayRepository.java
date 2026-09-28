package com.bsolz.lms.holiday.repository;

import com.bsolz.lms.holiday.entity.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

	@EntityGraph(attributePaths = { "locationIds", "departmentIds" })
	List<Holiday> findAllByDateBetweenOrderByDateAscNameAsc(LocalDate from, LocalDate to);

	@EntityGraph(attributePaths = { "locationIds", "departmentIds" })
	Optional<Holiday> findWithScopeById(UUID id);

	boolean existsByDateAndNameIgnoreCase(LocalDate date, String name);

	boolean existsByDateAndNameIgnoreCaseAndIdNot(LocalDate date, String name, UUID id);

}
