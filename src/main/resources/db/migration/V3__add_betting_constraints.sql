ALTER TABLE players
    ADD CONSTRAINT chk_player_balance_non_negative
        CHECK (balance >= 0);

ALTER TABLE bets
    ADD CONSTRAINT chk_bet_amount_positive
        CHECK (amount > 0);

ALTER TABLE bets
    ADD CONSTRAINT chk_bet_payout_non_negative
        CHECK (payout >= 0);