ALTER TABLE players
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PLAYER';

ALTER TABLE players
    ADD CONSTRAINT chk_players_role
        CHECK (role IN ('PLAYER', 'ADMIN'));