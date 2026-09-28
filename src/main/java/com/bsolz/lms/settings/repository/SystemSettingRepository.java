package com.bsolz.lms.settings.repository;

import com.bsolz.lms.settings.entity.SystemSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {

	@Query("select s from SystemSetting s")
	Optional<SystemSetting> findSingle();

}
