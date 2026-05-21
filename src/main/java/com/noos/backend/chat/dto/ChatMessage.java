package com.noos.backend.chat.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }

    private Long id;
    private MessageType type;
    private String roomId;
    private String sender;
    private Long senderId;
    private String content;
    private String role;
    private LocalDateTime timestamp;
}
