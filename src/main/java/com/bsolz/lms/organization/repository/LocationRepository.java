package com.bsolz.lms.organization.repository;

import com.bsolz.lms.organization.entity.Location;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, UUID> {

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

	@Query("select l from Location l where lower(l.code) in :lowerCodes")
	List<Location> findAllByLowerCodeIn(@Param("lowerCodes") Collection<String> lowerCodes);

}
