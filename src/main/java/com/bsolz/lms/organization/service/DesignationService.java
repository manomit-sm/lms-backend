package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.entity.Designation;
import com.bsolz.lms.organization.exception.OrganizationErrorCode;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.repository.DesignationRepository;
import com.bsolz.lms.organization.web.dto.DesignationRequest;
import com.bsolz.lms.organization.web.dto.DesignationResponse;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DesignationService {

	private final DesignationRepository designationRepository;

	private final OrganizationMapper mapper;

	@Transactional(readOnly = true)
	public List<DesignationResponse> list() {
		return designationRepository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public DesignationResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public DesignationResponse create(DesignationRequest request) {
		if (designationRepository.existsByNameIgnoreCase(request.name().trim())) {
			throw nameTaken(request.name());
		}
		Designation designation = new Designation();
		apply(designation, request);
		return mapper.toResponse(designationRepository.save(designation));
	}

	public DesignationResponse update(UUID id, DesignationRequest request) {
		Designation designation = require(id);
		if (designationRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
			throw nameTaken(request.name());
		}
		apply(designation, request);
		return mapper.toResponse(designation);
	}

	Designation require(UUID id) {
		return designationRepository.findById(id)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.DESIGNATION_NOT_FOUND,
						"Designation not found"));
	}

	private static void apply(Designation designation, DesignationRequest request) {
		designation.setName(request.name().trim());
		designation.setLevel(request.level());
		designation.setActive(request.active() == null || request.active());
	}

	private static ApiException nameTaken(String name) {
		return new ApiException(OrganizationErrorCode.DESIGNATION_NAME_TAKEN,
				"A designation named '" + name + "' already exists");
	}

}
