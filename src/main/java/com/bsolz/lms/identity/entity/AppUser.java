package com.bsolz.lms.identity.entity;

import com.bsolz.lms.identity.model.enums.UserStatus;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tenant user: an identity-provider subject plus roles. {@code employeeId} links to the
 * organization module's employee (by id only - identity doesn't map organization entities).
 */
@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

	private String email;

	@Setter
	private String idpSubject;

	@Enumerated(EnumType.STRING)
	private UserStatus status;

	@Setter
	private UUID employeeId;

	private Instant activatedAt;

	@ManyToMany
	@JoinTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<Role> roles = new HashSet<>();

	public static AppUser invite(String email) {
		AppUser user = new AppUser();
		user.email = email.trim().toLowerCase(Locale.ROOT);
		user.status = UserStatus.INVITED;
		return user;
	}

	/** First successful sign-in. */
	public void activate(Instant at) {
		if (status == UserStatus.INVITED) {
			status = UserStatus.ACTIVE;
			activatedAt = at;
		}
	}

	public void disable() {
		status = UserStatus.DISABLED;
	}

	public void enable() {
		if (status == UserStatus.DISABLED) {
			status = activatedAt == null ? UserStatus.INVITED : UserStatus.ACTIVE;
		}
	}

	public boolean isDisabled() {
		return status == UserStatus.DISABLED;
	}

	public void addRole(Role role) {
		roles.add(role);
	}

	public void replaceRoles(Collection<Role> newRoles) {
		roles.clear();
		roles.addAll(newRoles);
	}

	public boolean hasRole(String roleCode) {
		return roles.stream().anyMatch(role -> role.getCode().equals(roleCode));
	}

	public Set<String> getRoleCodes() {
		Set<String> codes = new TreeSet<>();
		roles.forEach(role -> codes.add(role.getCode()));
		return codes;
	}

	public Set<String> getPermissionCodes() {
		Set<String> codes = new TreeSet<>();
		roles.forEach(role -> codes.addAll(role.getPermissionCodes()));
		return codes;
	}

}
