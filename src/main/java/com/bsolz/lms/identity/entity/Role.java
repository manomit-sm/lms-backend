package com.bsolz.lms.identity.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

	private String code;

	private String name;

	private String description;

	/** Seeded roles; their definition can't be changed or deleted. */
	private boolean systemRole;

	@ManyToMany
	@JoinTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new HashSet<>();

	public Set<String> getPermissionCodes() {
		Set<String> codes = new TreeSet<>();
		permissions.forEach(permission -> codes.add(permission.getCode()));
		return codes;
	}

	public void replacePermissions(Collection<Permission> newPermissions) {
		permissions.clear();
		permissions.addAll(newPermissions);
	}

}
