CREATE UNIQUE INDEX idx_players_username_lower
    ON players (lower(username));