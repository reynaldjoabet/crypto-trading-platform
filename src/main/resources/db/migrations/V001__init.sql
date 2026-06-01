-- ============================================================================
-- V001 — Crypto Trading Platform initial schema
--
-- Money columns use NUMERIC(38, 18) so sub-satoshi and 18-decimal ERC-20s fit.
-- All identifiers are UUID v7-ish (generated app-side); we store them as `uuid`.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

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
    external_subject   TEXT         NOT NULL UNIQUE,
    country_iso        CHAR(2)      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- citext only if extension present; otherwise fall back to lower(email) index
CREATE EXTENSION IF NOT EXISTS citext;

-- ── Accounts / ledger ───────────────────────────────────────────────────────
CREATE TABLE accounts (
    id           UUID        PRIMARY KEY,
    user_id      UUID        REFERENCES users(id) ON DELETE RESTRICT,
    kind         TEXT        NOT NULL CHECK (kind IN
                  ('Cash','Trading','Reserve','FeesPayable','ExchangeOmnibus','CustodyOmnibus')),
    currency     TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, kind, currency)
);
CREATE INDEX accounts_user_idx ON accounts(user_id) WHERE user_id IS NOT NULL;

CREATE TABLE ledger_entries (
    id           UUID            PRIMARY KEY,
    account_id   UUID            NOT NULL REFERENCES accounts(id),
    direction    TEXT            NOT NULL CHECK (direction IN ('Debit','Credit')),
    amount       NUMERIC(38, 18) NOT NULL CHECK (amount >= 0),
    currency     TEXT            NOT NULL,
    reference    TEXT            NOT NULL,
    posted_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX ledger_account_posted_idx ON ledger_entries(account_id, posted_at DESC);
CREATE INDEX ledger_reference_idx      ON ledger_entries(reference);

-- ── Instruments ─────────────────────────────────────────────────────────────
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

CREATE TABLE strategy_component_links (
    strategy_id  UUID NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    component_id UUID NOT NULL REFERENCES strategy_components(id) ON DELETE RESTRICT,
    position     INT  NOT NULL,
    PRIMARY KEY (strategy_id, component_id)
);

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
CREATE INDEX orders_account_created_idx ON orders(account_id, created_at DESC);
CREATE INDEX orders_status_idx          ON orders(status) WHERE status IN ('Pending','Submitted','PartiallyFilled');

CREATE TABLE trades (
    id              UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL REFERENCES orders(id),
    venue_trade_id  TEXT            NOT NULL,
    price           NUMERIC(38, 18) NOT NULL CHECK (price > 0),
    quantity        NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    fee             NUMERIC(38, 18) NOT NULL CHECK (fee >= 0),
    fee_currency    TEXT            NOT NULL,
    executed_at     TIMESTAMPTZ     NOT NULL,
    UNIQUE (order_id, venue_trade_id)
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
    UNIQUE (user_id, chain, address)
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
CREATE INDEX forum_parent_idx ON forum_posts(parent_id);

-- Idempotency keys for safe retries of mutating endpoints
CREATE TABLE idempotency_keys (
    key          TEXT        PRIMARY KEY,
    scope        TEXT        NOT NULL,
    response     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idempotency_expires_idx ON idempotency_keys(expires_at);
