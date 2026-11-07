-- C-01: hội thoại và tin nhắn Messenger

CREATE TABLE conversation (
    id              BIGSERIAL PRIMARY KEY,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'MESSENGER',
    page_id         VARCHAR(64)  NOT NULL,   -- id Fanpage nhận tin
    sender_id       VARCHAR(64)  NOT NULL,   -- id của khách trên Messenger (PSID)
    status          VARCHAR(10)  NOT NULL DEFAULT 'AI',   -- AI hoặc NGUOI (C-05 dùng)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_page_sender UNIQUE (page_id, sender_id),
    CONSTRAINT ck_conversation_status CHECK (status IN ('AI', 'NGUOI'))
);

CREATE TABLE message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    direction       VARCHAR(3)   NOT NULL,   -- IN: khách gửi, OUT: hệ thống gửi
    external_id     VARCHAR(255),            -- mid của Facebook; chống lưu trùng
    body            TEXT         NOT NULL,
    sent_at         TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_message_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT uq_message_external_id UNIQUE (external_id)
);

CREATE INDEX idx_message_conversation ON message (conversation_id, sent_at);
