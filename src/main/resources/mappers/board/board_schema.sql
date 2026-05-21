CREATE TABLE IF NOT EXISTS board_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(20) NOT NULL DEFAULT 'FREE',
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    author_id BIGINT NOT NULL,
    views INT NOT NULL DEFAULT 0,
    likes INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_board_posts_category (category),
    INDEX idx_board_posts_author_id (author_id),
    INDEX idx_board_posts_created_at (created_at),
    INDEX idx_board_posts_pinned (pinned, created_at),
    CONSTRAINT fk_board_posts_author
        FOREIGN KEY (author_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS board_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    likes INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_board_comments_post_id (post_id),
    INDEX idx_board_comments_author_id (author_id),
    INDEX idx_board_comments_created_at (created_at),
    CONSTRAINT fk_board_comments_post
        FOREIGN KEY (post_id) REFERENCES board_posts(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_board_comments_author
        FOREIGN KEY (author_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS board_post_likes (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, post_id),
    CONSTRAINT fk_board_post_likes_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_board_post_likes_post
        FOREIGN KEY (post_id) REFERENCES board_posts(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS board_comment_likes (
    user_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, comment_id),
    CONSTRAINT fk_board_comment_likes_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_board_comment_likes_comment
        FOREIGN KEY (comment_id) REFERENCES board_comments(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
