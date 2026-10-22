ALTER TABLE storage_it_accounts ADD COLUMN note VARCHAR(64) NULL;

UPDATE storage_it_accounts SET note = 'created by migration' WHERE id = 1;
