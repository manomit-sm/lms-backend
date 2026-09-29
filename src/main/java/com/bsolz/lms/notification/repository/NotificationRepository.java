package com.bsolz.lms.notification.repository;

import com.bsolz.lms.notification.entity.Notification;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	/** @return 1 if inserted, 0 if the user already has a notification with this key */
	@Modifying
	@Query(value = """
			INSERT INTO notification (id, user_id, type, title, message, subject_type, subject_id, dedup_key, created_at)
			VALUES (:id, :userId, :type, :title, :message, :subjectType, :subjectId, :dedupKey, :createdAt)
			ON CONFLICT (user_id, dedup_key) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("type") String type,
			@Param("title") String title, @Param("message") String message, @Param("subjectType") String subjectType,
			@Param("subjectId") UUID subjectId, @Param("dedupKey") String dedupKey,
			@Param("createdAt") Instant createdAt);

	@Query("select n from Notification n where n.userId = :userId order by n.createdAt desc, n.id desc")
	Page<Notification> findForUser(@Param("userId") UUID userId, Pageable pageable);

	@Query("""
			select n from Notification n where n.userId = :userId and n.readAt is null
			order by n.createdAt desc, n.id desc
			""")
	Page<Notification> findUnreadForUser(@Param("userId") UUID userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(UUID userId);

	@Modifying
	@Query("update Notification n set n.readAt = :at where n.userId = :userId and n.id in :ids and n.readAt is null")
	int markRead(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("at") Instant at);

	@Modifying
	@Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") UUID userId, @Param("at") Instant at);

	boolean existsByIdAndUserId(UUID id, UUID userId);

}
