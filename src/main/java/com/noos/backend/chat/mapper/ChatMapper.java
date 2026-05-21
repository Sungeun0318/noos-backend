package com.noos.backend.chat.mapper;

import com.noos.backend.chat.dto.ChatMessage;
import com.noos.backend.chat.dto.ChatRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {

    void upsertRoom(ChatRoom room);

    void updateRoomAfterMessage(
            @Param("roomId") String roomId,
            @Param("lastMessage") String lastMessage,
            @Param("increaseUnread") boolean increaseUnread
    );

    void closeRoom(@Param("roomId") String roomId);

    void markRoomRead(@Param("roomId") String roomId);

    List<ChatRoom> findAllRooms();

    ChatRoom findRoomById(@Param("roomId") String roomId);

    void insertMessage(ChatMessage message);

    List<ChatMessage> findMessagesByRoomId(@Param("roomId") String roomId);
}
