CREATE TABLE IF NOT EXISTS chat_rooms (
    room_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    last_message VARCHAR(500) DEFAULT NULL,
    unread_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id),
    INDEX idx_chat_rooms_user_id (user_id),
    INDEX idx_chat_rooms_updated_at (updated_at),
    INDEX idx_chat_rooms_status (status),
    CONSTRAINT fk_chat_rooms_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id VARCHAR(100) NOT NULL,
    sender_id BIGINT NOT NULL,
    sender VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    type VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    content TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_chat_messages_room_id (room_id),
    INDEX idx_chat_messages_sender_id (sender_id),
    INDEX idx_chat_messages_created_at (created_at),
    CONSTRAINT fk_chat_messages_room
        FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
