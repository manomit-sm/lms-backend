package com.bsolz.lms.audit.repository;

import com.bsolz.lms.audit.entity.ActivityLog;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID>, JpaSpecificationExecutor<ActivityLog> {

	/** @return 1 if recorded, 0 if an entry for the event already exists */
	@Modifying
	@Query(value = """
			INSERT INTO activity_log (occurred_at, actor_user_id, actor_name, action, entity_type, entity_id,
			                          employee_id, employee_name, summary, details, event_key)
			VALUES (:occurredAt, :actorUserId, :actorName, :action, :entityType, :entityId, :employeeId, :employeeName,
			        :summary, CAST(:details AS jsonb), :eventKey)
			ON CONFLICT (event_key) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("occurredAt") Instant occurredAt, @Param("actorUserId") UUID actorUserId,
			@Param("actorName") String actorName, @Param("action") String action,
			@Param("entityType") String entityType, @Param("entityId") UUID entityId,
			@Param("employeeId") UUID employeeId, @Param("employeeName") String employeeName,
			@Param("summary") String summary, @Param("details") String details, @Param("eventKey") String eventKey);

	/** Entries about the given employees, or done by the actor. */
	@Query("""
			select a from ActivityLog a
			where a.employeeId in :employeeIds or a.actorUserId = :actorUserId
			order by a.occurredAt desc, a.seq desc
			""")
	Page<ActivityLog> findFeed(@Param("employeeIds") Collection<UUID> employeeIds,
			@Param("actorUserId") UUID actorUserId, Pageable pageable);

	@Query("select a from ActivityLog a order by a.occurredAt desc, a.seq desc")
	Page<ActivityLog> findLatest(Pageable pageable);

}
