-- Executable subset of the proposed Room contract, not the production schema.
-- Financial values here are supplied only by hand-authored synthetic tests.
PRAGMA foreign_keys = ON;
CREATE TABLE generation(id INTEGER PRIMARY KEY CHECK(id = 1), value INTEGER NOT NULL);
INSERT INTO generation VALUES(1, 0);
CREATE TABLE accounts(id TEXT PRIMARY KEY, origin TEXT NOT NULL);
CREATE TABLE batches(operation_id TEXT PRIMARY KEY, source_revision INTEGER NOT NULL);
CREATE TABLE snapshots(
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id TEXT NOT NULL REFERENCES accounts(id),
    as_of_date TEXT NOT NULL,
    amount_cents INTEGER NOT NULL CHECK(typeof(amount_cents) = 'integer'),
    operation_id TEXT NOT NULL REFERENCES batches(operation_id),
    UNIQUE(account_id, operation_id)
);
CREATE TRIGGER snapshots_no_update BEFORE UPDATE ON snapshots
BEGIN SELECT RAISE(ABORT, 'append_only'); END;
CREATE TRIGGER snapshots_no_delete BEFORE DELETE ON snapshots
BEGIN SELECT RAISE(ABORT, 'append_only'); END;
CREATE TABLE holdings(
    account_id TEXT NOT NULL REFERENCES accounts(id),
    security_id TEXT NOT NULL,
    operation_id TEXT NOT NULL REFERENCES batches(operation_id),
    quantity TEXT NOT NULL,
    PRIMARY KEY(account_id, security_id)
);
CREATE TABLE provider_state(
    account_id TEXT PRIMARY KEY REFERENCES accounts(id),
    accepted_revision INTEGER NOT NULL,
    accepted_at TEXT NOT NULL
);
CREATE VIEW current_balances AS
SELECT account_id, amount_cents, as_of_date FROM (
    SELECT *, ROW_NUMBER() OVER(PARTITION BY account_id ORDER BY as_of_date DESC, sequence DESC) AS rank
    FROM snapshots
) WHERE rank = 1;
