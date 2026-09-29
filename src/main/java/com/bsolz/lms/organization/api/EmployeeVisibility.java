package com.bsolz.lms.organization.api;

import com.bsolz.lms.shared.security.DataScope;
import java.util.UUID;

/**
 * Whose employee records the current user may see: themselves, their reporting line
 * ({@code EMPLOYEE_VIEW_TEAM}) or everyone ({@code EMPLOYEE_VIEW_ALL}). Other modules use it for
 * data that belongs to an employee, such as balances, so every module applies the same scope.
 */
public interface EmployeeVisibility {

	DataScope currentScope();

	boolean canView(UUID employeeId);

}
