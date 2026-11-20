-- ============================================================================
-- V001 — Crypto Trading Platform initial schema (targets PostgreSQL 19)
--
-- Money columns use NUMERIC(38, 18) so sub-satoshi and 18-decimal ERC-20s fit.
-- All identifiers are UUID v7-ish (generated app-side); we store them as `uuid`.
--
-- Index policy: every index below backs a query in trading.persistence.* or a
-- foreign key whose parent can be deleted/updated. Each one costs a write on
-- every insert, so don't add one without a query that needs it.
--
-- Every table has a primary key, which REPACK (CONCURRENTLY) requires when
-- reclaiming bloat online (see "Operations" at the end of this file).
-- ============================================================================

-- Must exist before any table uses the CITEXT type.
CREATE EXTENSION IF NOT EXISTS citext;

-- ── Users / auth ────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                 UUID         PRIMARY KEY,
    email              CITEXT       NOT NULL UNIQUE,
    display_name       TEXT         NOT NULL,
    role               TEXT         NOT NULL CHECK (role IN ('SuperAdmin','Admin','Client')),
    tier               TEXT         NOT NULL DEFAULT 'Bronze'
                                   CHECK (tier IN ('Bronze','Silver','Gold','Platinum','Diamond')),
    kyc_status         TEXT         NOT NULL DEFAULT 'NotStarted'
                                   CHECK (kyc_status IN ('NotStarted','Pending','Approved','Rejected')),
    external_subject   TEXT         NOT NULL UNIQUE,   -- upsert arbiter (UserRepo)
    country_iso        CHAR(2)      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Accounts / ledger ───────────────────────────────────────────────────────
-- user_id IS NULL marks a platform-owned account (FeesPayable, omnibus, ...).
CREATE TABLE accounts (
    id           UUID        PRIMARY KEY,
    user_id      UUID        REFERENCES users(id) ON DELETE RESTRICT,
    kind         TEXT        NOT NULL CHECK (kind IN
                  ('Cash','Trading','Reserve','FeesPayable','ExchangeOmnibus','CustodyOmnibus')),
    currency     TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULLS NOT DISTINCT: at most one platform account per (kind, currency).
    -- A plain UNIQUE treats NULLs as distinct and would allow duplicates.
    -- Its leading user_id column also serves lookups by owner and the FK
    -- check when a user is deleted, so no separate user_id index is needed.
    CONSTRAINT accounts_owner_kind_currency_key
        UNIQUE NULLS NOT DISTINCT (user_id, kind, currency)
);

-- Append-only: rows are inserted, never updated, so the table stays bloat-free.
CREATE TABLE ledger_entries (
    id           UUID            PRIMARY KEY,
    account_id   UUID            NOT NULL REFERENCES accounts(id),
    direction    TEXT            NOT NULL CHECK (direction IN ('Debit','Credit')),
    amount       NUMERIC(38, 18) NOT NULL CHECK (amount >= 0),
    currency     TEXT            NOT NULL,
    reference    TEXT            NOT NULL,
    posted_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);
-- Covers the balance query (AccountRepo.selectBalance) as an index-only scan:
-- the WHERE columns lead and the summed columns are INCLUDEd. On PG 19 reading
-- queries also set visibility-map bits, so on an insert-only table this keeps
-- working without waiting for vacuum. posted_at keeps per-account statement
-- history ordered by time from the same index.
CREATE INDEX ledger_account_currency_posted_idx
    ON ledger_entries (account_id, currency, posted_at)
    INCLUDE (direction, amount);
CREATE INDEX ledger_reference_idx ON ledger_entries (reference);

-- ── Instruments ─────────────────────────────────────────────────────────────
-- Small reference table; the UNIQUE index on symbol already serves the
-- ORDER BY symbol listings.
CREATE TABLE instruments (
    id           UUID        PRIMARY KEY,
    symbol       TEXT        NOT NULL UNIQUE,
    base         TEXT        NOT NULL,
    quote        TEXT        NOT NULL,
    venue        TEXT        NOT NULL CHECK (venue IN ('KRAKEN','BINANCE','FTX','MOCK')),
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Strategy components & strategies ────────────────────────────────────────
CREATE TABLE strategy_components (
    id            UUID        PRIMARY KEY,
    name          TEXT        NOT NULL,
    indicator     TEXT        NOT NULL,
    params        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE strategies (
    id              UUID        PRIMARY KEY,
    name            TEXT        NOT NULL,
    symbol          TEXT        NOT NULL,
    max_drawdown    DOUBLE PRECISION NOT NULL,
    max_leverage    INT         NOT NULL CHECK (max_leverage BETWEEN 1 AND 25),
    management_fee_bps   INT    NOT NULL CHECK (management_fee_bps BETWEEN 0 AND 10000),
    performance_fee_bps  INT    NOT NULL CHECK (performance_fee_bps BETWEEN 0 AND 10000),
    risk            TEXT        NOT NULL CHECK (risk IN ('Conservative','Balanced','Aggressive')),
    is_published    BOOLEAN     NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Published catalogue, newest first (StrategyRepo): rows come back already
-- sorted and drafts are never scanned.
CREATE INDEX strategies_published_updated_idx
    ON strategies (updated_at DESC) WHERE is_published;

CREATE TABLE strategy_component_links (
    strategy_id  UUID NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    component_id UUID NOT NULL REFERENCES strategy_components(id) ON DELETE RESTRICT,
    position     INT  NOT NULL,
    PRIMARY KEY (strategy_id, component_id)
);
-- The PK covers lookups by strategy_id. Deleting a component must check that
-- no link references it; without this index that check scans the whole table.
CREATE INDEX strategy_component_links_component_idx
    ON strategy_component_links (component_id);

-- ── Orders & trades ─────────────────────────────────────────────────────────
CREATE TABLE orders (
    id                UUID            PRIMARY KEY,
    account_id        UUID            NOT NULL REFERENCES accounts(id),
    instrument_id     UUID            NOT NULL REFERENCES instruments(id),
    strategy_id       UUID            REFERENCES strategies(id),
    side              TEXT            NOT NULL CHECK (side IN ('Buy','Sell')),
    order_type        TEXT            NOT NULL CHECK (order_type IN ('Market','Limit','StopLimit','TakeProfit')),
    quantity          NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    limit_price       NUMERIC(38, 18),
    stop_price        NUMERIC(38, 18),
    time_in_force     TEXT            NOT NULL CHECK (time_in_force IN ('Gtc','Ioc','Fok','Day')),
    status            TEXT            NOT NULL CHECK (status IN
                       ('Pending','Submitted','PartiallyFilled','Filled','Cancelled','Rejected','Failed')),
    venue             TEXT            NOT NULL,
    venue_order_id    TEXT,
    filled_qty        NUMERIC(38, 18) NOT NULL DEFAULT 0,
    avg_fill_price    NUMERIC(38, 18),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_event_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);
-- Per-account history, newest first, LIMIT n (OrderRepo).
CREATE INDEX orders_account_created_idx ON orders (account_id, created_at DESC);
-- Open-order sweep (OrderRepo): the predicate matches the query's IN list and
-- the key matches its ORDER BY created_at, so no sort is needed. The index
-- holds only live orders, so it stays small however long the history grows.
CREATE INDEX orders_open_created_idx
    ON orders (created_at)
    WHERE status IN ('Pending','Submitted','PartiallyFilled');
-- Lets deletes of instruments and strategies check for referencing orders
-- without scanning the whole table.
CREATE INDEX orders_instrument_idx ON orders (instrument_id);
CREATE INDEX orders_strategy_idx   ON orders (strategy_id) WHERE strategy_id IS NOT NULL;

CREATE TABLE trades (
    id              UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL REFERENCES orders(id),
    venue_trade_id  TEXT            NOT NULL,
    price           NUMERIC(38, 18) NOT NULL CHECK (price > 0),
    quantity        NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    fee             NUMERIC(38, 18) NOT NULL CHECK (fee >= 0),
    fee_currency    TEXT            NOT NULL,
    executed_at     TIMESTAMPTZ     NOT NULL,
    UNIQUE (order_id, venue_trade_id)   -- also serves trades-by-order and the FK
);

-- ── Wallets (user-linked addresses) ─────────────────────────────────────────
CREATE TABLE wallets (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users(id),
    label        TEXT        NOT NULL,
    chain        TEXT        NOT NULL,
    address      TEXT        NOT NULL,
    is_verified  BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, chain, address)    -- also serves wallets-by-user and the FK
);

-- ── Sustainable projects & donations ────────────────────────────────────────
CREATE TABLE sustainable_projects (
    id           UUID        PRIMARY KEY,
    name         TEXT        NOT NULL,
    description  TEXT        NOT NULL,
    target_amount NUMERIC(38, 18) NOT NULL CHECK (target_amount > 0),
    currency     TEXT        NOT NULL,
    raised_amount NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (raised_amount >= 0),
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE donations (
    id           UUID            PRIMARY KEY,
    user_id      UUID            NOT NULL REFERENCES users(id),
    project_id   UUID            NOT NULL REFERENCES sustainable_projects(id),
    amount       NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    currency     TEXT            NOT NULL,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX donations_user_idx    ON donations (user_id, created_at DESC);
CREATE INDEX donations_project_idx ON donations (project_id, created_at DESC);

-- ── Forum (lightweight; can be replaced by a real one later) ────────────────
CREATE TABLE forum_posts (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users(id),
    parent_id    UUID        REFERENCES forum_posts(id) ON DELETE CASCADE,
    title        TEXT,
    body         TEXT        NOT NULL,
    is_hidden    BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Replies to a post, oldest first; also used by the ON DELETE CASCADE.
CREATE INDEX forum_parent_idx ON forum_posts (parent_id, created_at) WHERE parent_id IS NOT NULL;
CREATE INDEX forum_user_idx   ON forum_posts (user_id);

-- ── Idempotency keys for safe retries of mutating endpoints ─────────────────
-- Claim-or-replay in one round trip with PG 19's ON CONFLICT DO SELECT:
--
--   INSERT INTO idempotency_keys (key, scope, expires_at)
--   VALUES ($1, $2, $3)
--   ON CONFLICT (key) DO SELECT FOR UPDATE
--   RETURNING key, scope, response, expires_at;
--
-- A row with response IS NULL is a claim still in progress; non-null means
-- replay the stored response. FOR UPDATE makes a concurrent retry wait for the
-- first request to finish instead of reading a half-done claim.
CREATE TABLE idempotency_keys (
    key          TEXT        PRIMARY KEY,
    scope        TEXT        NOT NULL,
    response     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL
);
-- Expiry sweeps: DELETE FROM idempotency_keys WHERE expires_at < now().
CREATE INDEX idempotency_expires_idx ON idempotency_keys (expires_at);

-- ── Operations (not run by this migration) ──────────────────────────────────
-- Reclaim bloat online on update-heavy tables (orders, idempotency_keys):
--     REPACK (CONCURRENTLY, ANALYZE) orders;
-- REPACK can't run inside a transaction block and Flyway wraps each migration
-- in one, so run it from a maintenance job, never from a migration.
