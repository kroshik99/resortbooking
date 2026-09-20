CREATE TABLE check_in_request (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT      NOT NULL REFERENCES booking(id),
    requested_by    BIGINT      NOT NULL REFERENCES app_user(id),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by     BIGINT REFERENCES app_user(id),
    reviewed_at     TIMESTAMPTZ
);

-- At most one open request per booking; the database enforces this, not just the service.
CREATE UNIQUE INDEX idx_one_pending_request_per_booking
    ON check_in_request(booking_id) WHERE (status = 'PENDING');

CREATE INDEX idx_checkin_request_status ON check_in_request(status);
