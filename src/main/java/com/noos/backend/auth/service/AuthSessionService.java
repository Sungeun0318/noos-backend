package com.noos.backend.auth.service;

import com.noos.backend.auth.dto.AuthSessionResponse;
import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import static com.noos.backend.auth.session.AuthSessionKeys.LOGIN_USER_ID;
import static com.noos.backend.auth.session.AuthSessionKeys.LOGIN_USER_LOGIN_ID;
import static com.noos.backend.auth.session.AuthSessionKeys.LOGIN_USER_NAME;
import static com.noos.backend.auth.session.AuthSessionKeys.LOGIN_USER_ROLE;

@Service
public class AuthSessionService {

    public SessionUser createLoginSession(HttpServletRequest request, User user) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        SessionUser sessionUser = new SessionUser(
                user.getUserId(),
                user.getLoginId(),
                user.getDisplayName(),
                user.getRole()
        );

        HttpSession session = request.getSession(true);
        session.setAttribute(LOGIN_USER_ID, sessionUser.userId());
        session.setAttribute(LOGIN_USER_LOGIN_ID, sessionUser.loginId());
        session.setAttribute(LOGIN_USER_NAME, sessionUser.displayName());
        session.setAttribute(LOGIN_USER_ROLE, sessionUser.role());

        return sessionUser;
    }

    public AuthSessionResponse getCurrentSession(HttpServletRequest request) {
        return AuthSessionResponse.fromSessionUser(getSessionUser(request));
    }

    public SessionUser getSessionUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        return getSessionUser(request.getSession(false));
    }

    public SessionUser getSessionUser(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object userId = session.getAttribute(LOGIN_USER_ID);
        if (!(userId instanceof Number number)) {
            return null;
        }

        return new SessionUser(
                number.longValue(),
                stringAttribute(session, LOGIN_USER_LOGIN_ID),
                stringAttribute(session, LOGIN_USER_NAME),
                stringAttribute(session, LOGIN_USER_ROLE)
        );
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private String stringAttribute(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value instanceof String stringValue ? stringValue : null;
    }
}
