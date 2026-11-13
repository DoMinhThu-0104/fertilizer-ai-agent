-- C-04: hàng đợi trả lời nằm trong database (không mất tin khi backend sập)
ALTER TABLE message
    ADD COLUMN reply_status   VARCHAR(12),   -- chỉ dùng cho tin khách gửi đến (IN)
    ADD COLUMN reply_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN reply_next_at  TIMESTAMPTZ,
    ADD COLUMN reply_error    VARCHAR(100),
    ADD CONSTRAINT ck_message_reply_status CHECK (
        reply_status IS NULL OR reply_status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'EXPIRED'));

CREATE INDEX idx_message_reply_queue ON message (reply_next_at)
    WHERE reply_status IN ('PENDING', 'PROCESSING');
