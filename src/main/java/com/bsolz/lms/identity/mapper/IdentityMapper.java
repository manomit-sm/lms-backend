package com.bsolz.lms.identity.mapper;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.entity.Permission;
import com.bsolz.lms.identity.entity.Role;
import com.bsolz.lms.identity.web.dto.PermissionResponse;
import com.bsolz.lms.identity.web.dto.RoleResponse;
import com.bsolz.lms.identity.web.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface IdentityMapper {

	@Mapping(target = "roles", source = "roleCodes")
	UserResponse toResponse(AppUser user);

	@Mapping(target = "permissions", source = "permissionCodes")
	RoleResponse toResponse(Role role);

	PermissionResponse toResponse(Permission permission);

}
