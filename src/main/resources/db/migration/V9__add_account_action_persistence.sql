DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        GROUP BY lower(btrim(email))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot normalize users.email because normalized email collisions exist';
    END IF;
END;
$$;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_email_key;

ALTER TABLE users
    ADD COLUMN email_normalized VARCHAR(255);

UPDATE users
SET email_normalized = lower(btrim(email));

ALTER TABLE users
    ALTER COLUMN email_normalized SET NOT NULL,
    ADD CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized),
    ADD CONSTRAINT chk_users_email_normalized_matches_email
        CHECK (email_normalized = lower(btrim(email)));

ALTER TABLE users
    ADD COLUMN verified_at TIMESTAMP;

UPDATE users
SET verified_at = COALESCE(verified_at, created_at, CURRENT_TIMESTAMP);

ALTER TABLE users
    ADD COLUMN auth_version INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_users_auth_version_non_negative CHECK (auth_version >= 0);

CREATE OR REPLACE FUNCTION prevent_user_username_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.username IS DISTINCT FROM NEW.username THEN
        RAISE EXCEPTION 'Username cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_username_is_immutable
BEFORE UPDATE OF username ON users
FOR EACH ROW
EXECUTE FUNCTION prevent_user_username_change();

CREATE TABLE account_action_requests (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    token_version INTEGER NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    invalidated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_action_request_purpose
        CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT chk_account_action_request_state
        CHECK (state IN ('PENDING', 'CONSUMED', 'INVALIDATED')),
    CONSTRAINT chk_account_action_request_token_version
        CHECK (token_version > 0),
    CONSTRAINT chk_account_action_request_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT chk_account_action_request_terminal_timestamps
        CHECK (
            (state = 'PENDING' AND consumed_at IS NULL AND invalidated_at IS NULL)
            OR (state = 'CONSUMED' AND consumed_at IS NOT NULL AND invalidated_at IS NULL)
            OR (state = 'INVALIDATED' AND consumed_at IS NULL AND invalidated_at IS NOT NULL)
        ),
    CONSTRAINT uq_account_action_requests_id_user_purpose
        UNIQUE (id, user_id, purpose)
);

CREATE INDEX idx_account_action_requests_user_purpose_state
    ON account_action_requests(user_id, purpose, state);

CREATE INDEX idx_account_action_requests_expires_at
    ON account_action_requests(expires_at);

CREATE UNIQUE INDEX uq_account_action_requests_current_pending
    ON account_action_requests(user_id, purpose)
    WHERE state = 'PENDING';

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_action_request_id UUID NOT NULL UNIQUE,
    kind VARCHAR(32) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP,
    sent_at TIMESTAMP,
    last_error_code VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_email_outbox_kind
        CHECK (kind IN ('ACCOUNT_ACTION')),
    CONSTRAINT chk_email_outbox_purpose
        CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT chk_email_outbox_state
        CHECK (state IN ('PENDING', 'PROCESSING', 'SENT', 'DEAD')),
    CONSTRAINT chk_email_outbox_attempts
        CHECK (attempts >= 0),
    CONSTRAINT chk_email_outbox_state_metadata
        CHECK (
            (state = 'PENDING'
                AND claimed_at IS NULL
                AND sent_at IS NULL
                AND last_error_code IS NULL)
            OR (state = 'PROCESSING'
                AND claimed_at IS NOT NULL
                AND sent_at IS NULL)
            OR (state = 'SENT'
                AND claimed_at IS NOT NULL
                AND sent_at IS NOT NULL
                AND last_error_code IS NULL)
            OR (state = 'DEAD'
                AND sent_at IS NULL
                AND last_error_code IS NOT NULL)
        ),
    CONSTRAINT fk_email_outbox_account_action_recipient_purpose
        FOREIGN KEY (account_action_request_id, recipient_user_id, purpose)
        REFERENCES account_action_requests(id, user_id, purpose)
        ON DELETE CASCADE
);

CREATE INDEX idx_email_outbox_dispatch
    ON email_outbox(state, available_at)
    WHERE state = 'PENDING';

CREATE INDEX idx_email_outbox_recipient_user
    ON email_outbox(recipient_user_id);

ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID;

UPDATE refresh_tokens
SET family_id = md5(id::text || clock_timestamp()::text || random()::text)::uuid;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_user_family
    ON refresh_tokens(user_id, family_id);
