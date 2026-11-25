CREATE TABLE runtime_probe (id INTEGER PRIMARY KEY, value VARCHAR(64) NOT NULL);
INSERT INTO runtime_probe (id, value) VALUES (1, 'migration-ready');
