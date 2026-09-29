package com.bsolz.lms.leavepolicy.repository;

import com.bsolz.lms.leavepolicy.entity.LeavePeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeavePeriodRepository extends JpaRepository<LeavePeriod, UUID> {

	List<LeavePeriod> findAllByOrderByStartDateDesc();

	Optional<LeavePeriod> findFirstByOrderByEndDateDesc();

	@Query("select p from LeavePeriod p where p.startDate <= :date and p.endDate >= :date")
	Optional<LeavePeriod> findContaining(@Param("date") LocalDate date);

	@Query("select count(p) > 0 from LeavePeriod p where p.startDate <= :endDate and p.endDate >= :startDate")
	boolean existsOverlapping(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	boolean existsByNameIgnoreCase(String name);

}
