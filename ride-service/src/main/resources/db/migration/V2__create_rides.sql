-- A ride booked by a user and the Stripe charge paying for it.
CREATE TABLE rides (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL REFERENCES users ON DELETE RESTRICT,
    -- The Idempotency-Key the ride was booked under. It outlives the idempotency record, so a request repeated
    -- after that record expired finds this ride instead of booking a second one.
    idempotency_key  UUID            NOT NULL UNIQUE,
    origin_lat       NUMERIC(13, 10) NOT NULL CHECK (origin_lat BETWEEN -90 AND 90),
    origin_lon       NUMERIC(13, 10) NOT NULL CHECK (origin_lon BETWEEN -180 AND 180),
    target_lat       NUMERIC(13, 10) NOT NULL CHECK (target_lat BETWEEN -90 AND 90),
    target_lon       NUMERIC(13, 10) NOT NULL CHECK (target_lon BETWEEN -180 AND 180),
    amount           BIGINT          NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)      NOT NULL,
    -- NULL until Stripe has confirmed the charge
    stripe_charge_id VARCHAR(255)    UNIQUE,
    charged_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CHECK ((stripe_charge_id IS NULL) = (charged_at IS NULL))
);

CREATE INDEX rides_user_id ON rides (user_id);
