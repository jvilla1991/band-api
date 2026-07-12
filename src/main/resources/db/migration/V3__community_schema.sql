-- YourArea community schema.
-- Extends users with community-profile fields and adds magic-link auth,
-- forum, bulletins, wall comments, and request-gated DM tables.

-- users: passwordless (magic-link) members have no password
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN username       VARCHAR(24) UNIQUE,
    ADD COLUMN official       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN display_name   VARCHAR(100),
    ADD COLUMN avatar_url     TEXT,
    ADD COLUMN about          TEXT,
    ADD COLUMN who_to_meet    TEXT,
    ADD COLUMN mood           VARCHAR(100),
    ADD COLUMN profile_song   VARCHAR(200),
    ADD COLUMN theme_accent   VARCHAR(20) NOT NULL DEFAULT 'EMBER',
    ADD COLUMN theme_glitter  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN theme_tiled_bg BOOLEAN NOT NULL DEFAULT FALSE;

-- one-time magic-link tokens (stored hashed, single-use, short-lived)
CREATE TABLE magic_link_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    email      VARCHAR(255) NOT NULL,
    username   VARCHAR(24),
    purpose    VARCHAR(10) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Top 8 friends (ordered refs to other members)
CREATE TABLE top_friends (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position  INT NOT NULL,
    UNIQUE (user_id, position),
    UNIQUE (user_id, friend_id)
);

-- forum: boards -> threads -> replies
CREATE TABLE boards (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(50) UNIQUE NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description TEXT,
    position    INT NOT NULL DEFAULT 0
);

CREATE TABLE forum_threads (
    id         BIGSERIAL PRIMARY KEY,
    board_id   BIGINT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    author_id  BIGINT NOT NULL REFERENCES users(id),
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_forum_threads_board ON forum_threads(board_id, created_at DESC);

CREATE TABLE forum_replies (
    id         BIGSERIAL PRIMARY KEY,
    thread_id  BIGINT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
    author_id  BIGINT NOT NULL REFERENCES users(id),
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_forum_replies_thread ON forum_replies(thread_id);

-- bulletins (official account only)
CREATE TABLE bulletins (
    id         BIGSERIAL PRIMARY KEY,
    author_id  BIGINT NOT NULL REFERENCES users(id),
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- profile wall comments
CREATE TABLE wall_comments (
    id              BIGSERIAL PRIMARY KEY,
    profile_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body            TEXT NOT NULL,
    glitter         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wall_comments_profile ON wall_comments(profile_user_id, created_at DESC);

-- request-gated DMs
CREATE TABLE dm_requests (
    id           BIGSERIAL PRIMARY KEY,
    sender_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body         TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_dm_requests_recipient ON dm_requests(recipient_id, status);

-- one thread per user pair (user_a_id < user_b_id, normalized in code)
CREATE TABLE dm_threads (
    id            BIGSERIAL PRIMARY KEY,
    user_a_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    revoked_by_id BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_a_id, user_b_id)
);

CREATE TABLE dm_messages (
    id         BIGSERIAL PRIMARY KEY,
    thread_id  BIGINT NOT NULL REFERENCES dm_threads(id) ON DELETE CASCADE,
    sender_id  BIGINT NOT NULL REFERENCES users(id),
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dm_messages_thread ON dm_messages(thread_id, created_at);

-- per-user thread state: unread marker + trash (soft delete)
CREATE TABLE dm_thread_states (
    id           BIGSERIAL PRIMARY KEY,
    thread_id    BIGINT NOT NULL REFERENCES dm_threads(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at TIMESTAMPTZ,
    trashed      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (thread_id, user_id)
);
