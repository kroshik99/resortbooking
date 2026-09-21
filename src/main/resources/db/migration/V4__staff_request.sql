CREATE TABLE staff_request (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app_user(id),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by     BIGINT REFERENCES app_user(id),
    reviewed_at     TIMESTAMPTZ
);

-- At most one open request per user; the database enforces this, not just the service.
CREATE UNIQUE INDEX idx_one_pending_staff_request_per_user
    ON staff_request(user_id) WHERE (status = 'PENDING');

CREATE INDEX idx_staff_request_status ON staff_request(status);
