CREATE INDEX idx_bets_game_id
    ON bets (game_id);

CREATE INDEX idx_bets_player_id_created_at
    ON bets (player_id, created_at DESC);