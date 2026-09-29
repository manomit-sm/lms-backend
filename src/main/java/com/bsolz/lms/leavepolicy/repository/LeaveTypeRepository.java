package com.bsolz.lms.leavepolicy.repository;

import com.bsolz.lms.leavepolicy.entity.LeaveType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

	List<LeaveType> findAllByOrderBySortOrderAscNameAsc();

	List<LeaveType> findAllByActiveTrueOrderBySortOrderAscNameAsc();

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, UUID id);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

}
