CREATE TABLE IF NOT EXISTS player_states (
    uuid TEXT PRIMARY KEY,
    data TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
