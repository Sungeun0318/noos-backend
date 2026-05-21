package com.noos.backend.auth.dto;

import com.noos.backend.auth.session.SessionUser;

public record AuthSessionResponse(
        boolean authenticated,
        Long userId,
        String loginId,
        String displayName,
        String role
) {
    public static AuthSessionResponse anonymous() {
        return new AuthSessionResponse(false, null, null, null, null);
    }

    public static AuthSessionResponse fromUser(User user) {
        if (user == null) {
            return anonymous();
        }

        return fromSessionUser(new SessionUser(
                user.getUserId(),
                user.getLoginId(),
                user.getDisplayName(),
                user.getRole()
        ));
    }

    public static AuthSessionResponse fromSessionUser(SessionUser sessionUser) {
        if (sessionUser == null) {
            return anonymous();
        }

        return new AuthSessionResponse(
                true,
                sessionUser.userId(),
                sessionUser.loginId(),
                sessionUser.displayName(),
                sessionUser.role()
        );
    }
}
