package com.bsolz.lms.platform.mapper;

import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.platform.web.dto.TenantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface TenantMapper {

	@Mapping(target = "key", source = "tenantKey")
	TenantResponse toResponse(Tenant tenant);

}
