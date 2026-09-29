package com.bsolz.lms.settings.service;

import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.settings.api.SystemSettings;
import com.bsolz.lms.settings.entity.SystemSetting;
import com.bsolz.lms.settings.exception.SettingsErrorCode;
import com.bsolz.lms.settings.repository.SystemSettingRepository;
import com.bsolz.lms.settings.web.dto.SettingsRequest;
import com.bsolz.lms.settings.web.dto.SettingsResponse;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant settings. Changing the leave year start month affects leave periods created afterwards,
 * never existing ones.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SettingsService implements SettingsApi {

	/** Display patterns the frontend supports. */
	public static final List<String> DATE_FORMATS = List.of("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd-MM-yyyy",
			"dd.MM.yyyy", "dd MMM yyyy");

	private final SystemSettingRepository repository;

	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public SystemSettings current() {
		SystemSetting setting = require();
		return new SystemSettings(setting.getLeaveYearStartMonth(), effectiveTimezone(setting),
				setting.getDateFormat(), setting.getWeekStartDay());
	}

	@Override
	@Transactional(readOnly = true)
	public LocalDate today() {
		return LocalDate.now(clock.withZone(effectiveTimezone(require())));
	}

	@Transactional(readOnly = true)
	public SettingsResponse get() {
		return toResponse(require());
	}

	public SettingsResponse update(SettingsRequest request) {
		if (request.timezone() != null) {
			try {
				ZoneId.of(request.timezone());
			}
			catch (DateTimeException ex) {
				throw new ApiException(SettingsErrorCode.INVALID_TIMEZONE, "Unknown timezone: " + request.timezone());
			}
		}
		if (!DATE_FORMATS.contains(request.dateFormat())) {
			throw new ApiException(SettingsErrorCode.UNSUPPORTED_DATE_FORMAT,
					"Date format must be one of " + DATE_FORMATS);
		}
		SystemSetting setting = require();
		setting.setLeaveYearStartMonth(request.leaveYearStartMonth());
		setting.setTimezone(request.timezone());
		setting.setDateFormat(request.dateFormat());
		setting.setWeekStartDay(request.weekStartDay());
		setting.setOrganizationName(request.organizationName() == null ? null : request.organizationName().trim());
		setting.setLogoUrl(request.logoUrl());
		repository.flush();
		return toResponse(setting);
	}

	private SystemSetting require() {
		return repository.findSingle()
				.orElseThrow(() -> new IllegalStateException("Tenant schema has no system_setting row"));
	}

	private static ZoneId effectiveTimezone(SystemSetting setting) {
		return setting.getTimezone() != null ? ZoneId.of(setting.getTimezone()) : TenantContext.require().timezone();
	}

	private static SettingsResponse toResponse(SystemSetting setting) {
		return new SettingsResponse(setting.getLeaveYearStartMonth(), setting.getTimezone(),
				effectiveTimezone(setting).getId(), setting.getDateFormat(), setting.getWeekStartDay(),
				setting.getOrganizationName(), setting.getLogoUrl(), setting.getUpdatedAt());
	}

}
