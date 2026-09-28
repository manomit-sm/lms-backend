package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.api.EmployeeVisibility;
import com.bsolz.lms.organization.repository.EmployeeRepository;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.DataScope;
import com.bsolz.lms.shared.security.LmsPrincipal;
import com.bsolz.lms.shared.security.Permissions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Employee data scope for the current user: everyone sees themselves; {@code EMPLOYEE_VIEW_TEAM}
 * adds their reporting line; {@code EMPLOYEE_VIEW_ALL} sees the whole tenant. Used in
 * {@code @PreAuthorize("@employeeAccess.canView(#employeeId)")} and to filter employee lists.
 */
@Component("employeeAccess")
@RequiredArgsConstructor
public class EmployeeAccess implements EmployeeVisibility {

	private final EmployeeRepository employeeRepository;

	@Override
	public DataScope currentScope() {
		return DataScope.from(CurrentUser.require(), Permissions.EMPLOYEE_VIEW_ALL, Permissions.EMPLOYEE_VIEW_TEAM);
	}

	@Override
	public boolean canView(UUID employeeId) {
		LmsPrincipal principal = CurrentUser.require();
		UUID self = principal.employeeId();
		return switch (currentScope()) {
			case TENANT -> true;
			case TEAM -> employeeId.equals(self) || employeeRepository.isInReportingLine(self, employeeId);
			case SELF -> employeeId.equals(self);
		};
	}

}
