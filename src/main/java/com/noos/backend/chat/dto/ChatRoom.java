package com.noos.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    private String roomId;
    private Long userId;
    private String userName;
    private String lastMessage;
    private int unreadCount;
    private String updatedAt;
    private String status;

    public ChatRoom(String roomId, String userName) {
        this.roomId = roomId;
        this.userName = userName;
        this.status = "ACTIVE";
    }
}
