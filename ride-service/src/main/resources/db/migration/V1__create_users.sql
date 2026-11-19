-- A rider of the service. Authentication is out of scope: the Authorization header names the user by email.
CREATE TABLE users (
    id                 BIGSERIAL    PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    -- Stripe customer with a default payment source; every charge of the user goes to it
    stripe_customer_id VARCHAR(255) NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
