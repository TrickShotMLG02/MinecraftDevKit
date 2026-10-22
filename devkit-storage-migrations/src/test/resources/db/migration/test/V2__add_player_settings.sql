CREATE TABLE player_settings (
    uuid VARCHAR(36) PRIMARY KEY,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);
