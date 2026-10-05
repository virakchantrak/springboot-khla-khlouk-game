CREATE TABLE players
(
    id         UUID PRIMARY KEY,
    username   VARCHAR(100)             NOT NULL UNIQUE,
    balance    NUMERIC(19, 2)           NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE games
(
    id         UUID PRIMARY KEY,
    status     VARCHAR(20)              NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at   TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE bets
(
    id         UUID PRIMARY KEY,
    game_id    UUID                     NOT NULL,
    player_id  UUID                     NOT NULL,
    symbol     VARCHAR(30)              NOT NULL,
    amount     NUMERIC(19, 2)           NOT NULL,
    payout     NUMERIC(19, 2)           NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_bet_game
        FOREIGN KEY (game_id) REFERENCES games (id),

    CONSTRAINT fk_bet_player
        FOREIGN KEY (player_id) REFERENCES players (id)
);

CREATE TABLE game_results
(
    id         UUID PRIMARY KEY,
    game_id    UUID                     NOT NULL UNIQUE,
    dice1      VARCHAR(30)              NOT NULL,
    dice2      VARCHAR(30)              NOT NULL,
    dice3      VARCHAR(30)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_result_game
        FOREIGN KEY (game_id) REFERENCES games (id)
);