CREATE TABLE wallet_transactions
(
    id            UUID PRIMARY KEY,
    player_id     UUID                     NOT NULL,
    type          VARCHAR(20)              NOT NULL,
    amount        NUMERIC(19, 2)           NOT NULL,
    balance_after NUMERIC(19, 2)           NOT NULL,
    reference_id  UUID,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_wallet_tx_player
        FOREIGN KEY (player_id) REFERENCES players (id),

    CONSTRAINT chk_wallet_tx_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_wallet_tx_balance_non_negative
        CHECK (balance_after >= 0)
);

CREATE INDEX idx_wallet_tx_player_created
    ON wallet_transactions (player_id, created_at DESC);