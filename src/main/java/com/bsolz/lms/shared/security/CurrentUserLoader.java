package com.bsolz.lms.shared.security;

import java.util.Optional;

/**
 * Loads the authenticated subject's user, roles and permissions from the current tenant's schema.
 * Called by {@code TenantContextFilter} with the tenant already bound. Implemented by the
 * {@code identity} module; until an implementation exists, authenticated tenant requests carry no
 * authorities.
 */
public interface CurrentUserLoader {

	Optional<UserAccess> loadBySubject(String subject);

}
