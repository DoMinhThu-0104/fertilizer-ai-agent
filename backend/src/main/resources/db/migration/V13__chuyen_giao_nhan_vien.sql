-- C-05: chuyển giao giữa AI và nhân viên
ALTER TABLE conversation
    ADD COLUMN handoff_reason VARCHAR(30),
    ADD COLUMN handoff_at     TIMESTAMPTZ;

CREATE INDEX idx_conversation_cho_nhan_vien
    ON conversation (handoff_at) WHERE status = 'NGUOI';
