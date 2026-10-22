CREATE TABLE storage_it_accounts (
    id BIGINT NOT NULL PRIMARY KEY,
    balance BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO storage_it_accounts (id, balance) VALUES (1, 1);
