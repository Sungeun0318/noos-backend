package com.noos.backend.chat.controller;

import com.noos.backend.chat.dto.ChatMessage;
import com.noos.backend.chat.dto.ChatRoom;
import com.noos.backend.chat.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage message) {
        ChatMessage savedMessage = chatService.saveChatMessage(message);
        messagingTemplate.convertAndSend("/topic/room." + savedMessage.getRoomId(), savedMessage);
    }

    @MessageMapping("/chat.join")
    public void joinRoom(@Payload ChatMessage message) {
        ChatMessage joinedMessage = chatService.joinRoom(message);
        messagingTemplate.convertAndSend("/topic/room." + joinedMessage.getRoomId(), joinedMessage);
    }

    @MessageMapping("/chat.leave")
    public void leaveRoom(@Payload ChatMessage message) {
        ChatMessage leftMessage = chatService.leaveRoom(message);
        messagingTemplate.convertAndSend("/topic/room." + leftMessage.getRoomId(), leftMessage);
    }

    @ResponseBody
    @GetMapping("/api/chat/rooms")
    public ResponseEntity<List<ChatRoom>> getRooms(HttpSession session) {
        return ResponseEntity.ok(chatService.findAllRooms(session));
    }

    @ResponseBody
    @GetMapping("/api/chat/history/{roomId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String roomId, HttpSession session) {
        return ResponseEntity.ok(chatService.findHistory(roomId, session));
    }
}
