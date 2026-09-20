CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE room_type (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(50)    NOT NULL UNIQUE,
    capacity     INT            NOT NULL CHECK (capacity > 0),
    base_price   NUMERIC(10,2)  NOT NULL CHECK (base_price >= 0),
    description  TEXT
);

CREATE TABLE room (
    id             BIGSERIAL PRIMARY KEY,
    room_number    VARCHAR(10) NOT NULL UNIQUE,
    room_type_id   BIGINT      NOT NULL REFERENCES room_type(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE', 'MAINTENANCE'))
);
CREATE INDEX idx_room_type ON room(room_type_id);

CREATE TABLE seasonal_rate (
    id             BIGSERIAL PRIMARY KEY,
    room_type_id   BIGINT         NOT NULL REFERENCES room_type(id),
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    price          NUMERIC(10,2)  NOT NULL CHECK (price >= 0),
    CHECK (end_date > start_date),
    EXCLUDE USING gist (room_type_id WITH =, daterange(start_date, end_date) WITH &&)
);

CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('GUEST', 'FRONT_DESK', 'ADMIN')),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE guest (
    id          BIGSERIAL PRIMARY KEY,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    phone       VARCHAR(20),
    user_id     BIGINT UNIQUE REFERENCES app_user(id)
);

CREATE TABLE booking (
    id            BIGSERIAL PRIMARY KEY,
    reference     VARCHAR(20)    NOT NULL UNIQUE,
    guest_id      BIGINT         NOT NULL REFERENCES guest(id),
    room_id       BIGINT         NOT NULL REFERENCES room(id),
    check_in      DATE           NOT NULL,
    check_out     DATE           NOT NULL,
    num_guests    INT            NOT NULL CHECK (num_guests > 0),
    total_price   NUMERIC(10,2)  NOT NULL CHECK (total_price >= 0),
    status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED')),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       INT            NOT NULL DEFAULT 0,
    CHECK (check_out > check_in),
    CONSTRAINT no_overlap EXCLUDE USING gist (
        room_id WITH =, daterange(check_in, check_out) WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX idx_booking_room_dates ON booking(room_id, check_in, check_out);
CREATE INDEX idx_booking_guest ON booking(guest_id);
CREATE INDEX idx_booking_status ON booking(status);

CREATE SEQUENCE booking_ref_seq START 1;
