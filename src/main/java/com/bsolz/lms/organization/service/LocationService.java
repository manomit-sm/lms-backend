package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.entity.Location;
import com.bsolz.lms.organization.exception.OrganizationErrorCode;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.repository.LocationRepository;
import com.bsolz.lms.organization.web.dto.LocationRequest;
import com.bsolz.lms.organization.web.dto.LocationResponse;
import com.bsolz.lms.shared.exception.ApiException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class LocationService {

	private final LocationRepository locationRepository;

	private final WorkScheduleService workScheduleService;

	private final OrganizationMapper mapper;

	@Transactional(readOnly = true)
	public List<LocationResponse> list() {
		return locationRepository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public LocationResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public LocationResponse create(LocationRequest request) {
		if (locationRepository.existsByCodeIgnoreCase(request.code().trim())) {
			throw codeTaken(request.code());
		}
		Location location = new Location();
		apply(location, request);
		return mapper.toResponse(locationRepository.save(location));
	}

	public LocationResponse update(UUID id, LocationRequest request) {
		Location location = require(id);
		if (locationRepository.existsByCodeIgnoreCaseAndIdNot(request.code().trim(), id)) {
			throw codeTaken(request.code());
		}
		apply(location, request);
		return mapper.toResponse(location);
	}

	Location require(UUID id) {
		return locationRepository.findById(id)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.LOCATION_NOT_FOUND, "Location not found"));
	}

	private void apply(Location location, LocationRequest request) {
		try {
			ZoneId.of(request.timezone());
		}
		catch (DateTimeException ex) {
			throw new ApiException(OrganizationErrorCode.INVALID_TIMEZONE, "Unknown timezone: " + request.timezone());
		}
		location.setCode(request.code().trim());
		location.setName(request.name().trim());
		location.setCountryCode(request.countryCode());
		location.setTimezone(request.timezone());
		location.setWorkSchedule(request.workScheduleId() == null ? null
				: workScheduleService.require(request.workScheduleId()));
		location.setActive(request.active() == null || request.active());
	}

	private static ApiException codeTaken(String code) {
		return new ApiException(OrganizationErrorCode.LOCATION_CODE_TAKEN, "Location code '" + code + "' is taken");
	}

}
