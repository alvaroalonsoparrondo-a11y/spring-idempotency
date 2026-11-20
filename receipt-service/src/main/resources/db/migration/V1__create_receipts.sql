-- The receipt of a charged ride, one per ride whatever the number of deliveries of its job.
CREATE TABLE receipts (
    id               BIGSERIAL    PRIMARY KEY,
    ride_id          BIGINT       NOT NULL UNIQUE,
    user_id          BIGINT       NOT NULL,
    stripe_charge_id VARCHAR(255) NOT NULL,
    amount           BIGINT       NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)   NOT NULL,
    charged_at       TIMESTAMPTZ  NOT NULL,
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX receipts_user_id ON receipts (user_id);
