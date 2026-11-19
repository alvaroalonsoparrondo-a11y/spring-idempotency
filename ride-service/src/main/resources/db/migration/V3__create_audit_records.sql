-- What happened to which resource, by whom and from where, written in the transaction of the change itself.
CREATE TABLE audit_records (
    id            BIGSERIAL   PRIMARY KEY,
    -- for example CREATE_RIDE
    action        VARCHAR(50) NOT NULL,
    data          JSONB       NOT NULL,
    origin_ip     INET        NOT NULL,
    -- for example RIDE and its id
    resource_type VARCHAR(50) NOT NULL,
    resource_id   BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL REFERENCES users ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_records_resource ON audit_records (resource_type, resource_id);
