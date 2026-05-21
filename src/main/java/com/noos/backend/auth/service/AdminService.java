package com.noos.backend.auth.service;

import com.noos.backend.auth.dto.UpdateUserRequest;
import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.dto.UserSearchRequest;
import com.noos.backend.auth.mapper.AdminMapper;
import com.noos.backend.auth.session.SessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    public AdminService(
            AdminMapper adminMapper,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService
    ) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    public List<User> findAllUsers(HttpSession session) {
        requireAdmin(session);
        return adminMapper.findAllUsers();
    }

    public List<User> searchUsers(UserSearchRequest request, HttpSession session) {
        String type = request.getType();
        String keyword = request.getKeyword();
        String startDate = request.getStartDate();
        String endDate = request.getEndDate();

        requireAdmin(session);
        return adminMapper.searchUsers(
                normalize(type),
                normalize(keyword),
                normalize(startDate),
                normalize(endDate)
        );
    }

    public void updateUser(Long userId, UpdateUserRequest request, HttpSession session) {
        requireAdmin(session);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        adminMapper.updateUser(userId, request);
    }

    public void deleteUser(Long userId, HttpSession session) {
        requireAdmin(session);
        adminMapper.deleteUser(userId);
    }

    public String describeCurrentAdmin(HttpSession session) {
        SessionUser sessionUser = requireAdmin(session);
        if (sessionUser.displayName() == null) {
            return "로그인 안됨";
        }
        return "이름: " + sessionUser.displayName()
                + " / 권한: " + sessionUser.role();
    }

    private SessionUser requireAdmin(HttpSession session) {
        SessionUser sessionUser = authSessionService.getSessionUser(session);
        if (sessionUser == null || !sessionUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required.");
        }
        return sessionUser;
    }

    private String normalize(String value) {
        return value != null ? value : "";
    }
}
