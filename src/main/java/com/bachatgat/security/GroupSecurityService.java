package com.bachatgat.security;

import com.bachatgat.exception.ForbiddenException;
import com.bachatgat.exception.UnauthorizedException;
import com.bachatgat.model.Role;
import org.springframework.stereotype.Service;

@Service
public class GroupSecurityService {

    /**
     * Validates that the authenticated principal is authorized to access the requested groupId.
     * SUPER_ADMIN can access any group.
     * ADMIN can only access their assigned groupId.
     * USER cannot access admin endpoints.
     */
    public void validateGroupAccess(UserPrincipal principal, String requestedGroupId) {
        if (principal == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        if (principal.getRole() == Role.SUPER_ADMIN) {
            return; // Super Admin has global visibility
        }

        if (principal.getRole() == Role.ADMIN || principal.getRole() == Role.PRESIDENT || principal.getRole() == Role.SECRETARY || principal.getRole() == Role.TREASURER) {
            String authorizedGroupId = principal.getGroupId();
            if (requestedGroupId == null || requestedGroupId.isBlank()) {
                return; // Defaults to caller's own authorized group
            }
            if (authorizedGroupId != null && authorizedGroupId.equalsIgnoreCase(requestedGroupId)) {
                return;
            }
            throw new ForbiddenException("Cross-group access denied: You are only authorized to access group " + authorizedGroupId);
        }

        throw new ForbiddenException("Access denied: Insufficient administrative privileges");
    }

    /**
     * Resolves the effective groupId for the caller:
     * - For Group Admin / President / Secretary / Treasurer, always returns their assigned groupId regardless of input.
     * - For SUPER_ADMIN, returns requestedGroupId if provided, otherwise null.
     */
    public String resolveEffectiveGroupId(UserPrincipal principal, String requestedGroupId) {
        if (principal == null) {
            throw new UnauthorizedException("User is not authenticated");
        }
        if (principal.getRole() == Role.ADMIN || principal.getRole() == Role.PRESIDENT || principal.getRole() == Role.SECRETARY || principal.getRole() == Role.TREASURER) {
            return principal.getGroupId();
        }
        return requestedGroupId;
    }

    public String resolveTargetGroupId(UserPrincipal principal, String requestedGroupId) {
        return resolveEffectiveGroupId(principal, requestedGroupId);
    }
}
