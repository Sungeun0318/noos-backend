package com.noos.backend.auth.session;

public record SessionUser(
        Long userId,
        String loginId,
        String displayName,
        String role
) {
    private static final String DEFAULT_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";

    public SessionUser {
        role = role != null && !role.isBlank() ? role.trim() : DEFAULT_ROLE;
    }

    public boolean isAdmin() {
        return ADMIN_ROLE.equals(role);
    }
}
