package com.noos.backend.auth.controller;

import com.noos.backend.auth.dto.UpdateUserRequest;
import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.dto.UserSearchRequest;
import com.noos.backend.auth.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/me")
    public ResponseEntity<String> me(HttpSession session) {
        return ResponseEntity.ok(adminService.describeCurrentAdmin(session));
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers(HttpSession session) {
        return ResponseEntity.ok(adminService.findAllUsers(session));
    }

    @GetMapping("/search/users")
    public ResponseEntity<List<User>> searchUsers(UserSearchRequest request, HttpSession session) {
        return ResponseEntity.ok(adminService.searchUsers(request, session));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<String> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest request,
            HttpSession session
    ) {
        adminService.updateUser(userId, request, session);
        return ResponseEntity.ok("수정 완료");
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId, HttpSession session) {
        adminService.deleteUser(userId, session);
        return ResponseEntity.ok("삭제 완료");
    }
}
