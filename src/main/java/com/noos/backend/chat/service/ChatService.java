package com.noos.backend.chat.service;

import com.noos.backend.auth.service.AuthSessionService;
import com.noos.backend.auth.session.SessionUser;
import com.noos.backend.chat.dto.ChatMessage;
import com.noos.backend.chat.dto.ChatRoom;
import com.noos.backend.chat.mapper.ChatMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {

    private final ChatMapper chatMapper;
    private final AuthSessionService authSessionService;

    public ChatService(ChatMapper chatMapper, AuthSessionService authSessionService) {
        this.chatMapper = chatMapper;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public ChatMessage saveChatMessage(ChatMessage message) {
        ChatMessage normalized = normalizeMessage(message, ChatMessage.MessageType.CHAT);
        upsertRoom(normalized);
        chatMapper.insertMessage(normalized);
        chatMapper.updateRoomAfterMessage(
                normalized.getRoomId(),
                normalized.getContent(),
                !"ADMIN".equals(normalized.getRole())
        );
        return normalized;
    }

    @Transactional
    public ChatMessage joinRoom(ChatMessage message) {
        ChatMessage normalized = normalizeMessage(message, ChatMessage.MessageType.JOIN);
        upsertRoom(normalized);
        chatMapper.insertMessage(normalized);
        return normalized;
    }

    @Transactional
    public ChatMessage leaveRoom(ChatMessage message) {
        ChatMessage normalized = normalizeMessage(message, ChatMessage.MessageType.LEAVE);
        upsertRoom(normalized);
        chatMapper.insertMessage(normalized);
        chatMapper.closeRoom(normalized.getRoomId());
        return normalized;
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> findAllRooms(HttpSession session) {
        requireAdmin(session);
        return chatMapper.findAllRooms();
    }

    @Transactional
    public List<ChatMessage> findHistory(String roomId, HttpSession session) {
        SessionUser sessionUser = authSessionService.getSessionUser(session);
        boolean admin = sessionUser != null && sessionUser.isAdmin();
        Long userId = sessionUser != null ? sessionUser.userId() : null;

        if (!admin && (userId == null || !roomId.equals(String.valueOf(userId)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat room access denied.");
        }
        if (admin) {
            chatMapper.markRoomRead(roomId);
        }
        return chatMapper.findMessagesByRoomId(roomId);
    }

    private void upsertRoom(ChatMessage message) {
        ChatRoom room = new ChatRoom(message.getRoomId(), message.getSender());
        room.setUserId(parseRoomUserId(message.getRoomId()));
        room.setStatus("ACTIVE");
        chatMapper.upsertRoom(room);
    }

    private ChatMessage normalizeMessage(ChatMessage message, ChatMessage.MessageType type) {
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body is required.");
        }
        if (message.getRoomId() == null || message.getRoomId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required.");
        }
        if (message.getSender() == null || message.getSender().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sender is required.");
        }
        message.setRoomId(message.getRoomId().trim());
        message.setSender(message.getSender().trim());
        message.setType(type);
        message.setTimestamp(LocalDateTime.now());
        if (message.getSenderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "senderId is required.");
        }
        if (message.getContent() == null) {
            message.setContent("");
        }
        if (message.getRole() == null || message.getRole().isBlank()) {
            message.setRole("USER");
        }
        return message;
    }

    private void requireAdmin(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required.");
        }
    }

    private boolean isAdmin(HttpSession session) {
        SessionUser sessionUser = authSessionService.getSessionUser(session);
        return sessionUser != null && sessionUser.isAdmin();
    }

    private Long parseRoomUserId(String roomId) {
        try {
            return Long.valueOf(roomId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId must be a user id.");
        }
    }
}
