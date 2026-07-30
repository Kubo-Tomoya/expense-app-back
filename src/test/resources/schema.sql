-- ============================================================
-- テスト実行時（Testcontainers起動の空のPostgreSQL）専用のスキーマ定義。
-- 本番のupdated_at自動更新トリガーは、Spring Bootのschema.sql実行機能が
-- PL/pgSQL関数定義内の$$...$$記法を正しく解釈できず、
-- セミコロンの位置で誤って文を区切ってしまうため、テスト環境では除外している。
-- アプリ側のコードが明示的にsetUpdatedAt()を呼んでいるため、
-- テストの結果自体には影響しない
-- ============================================================

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    name VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    -- F-28：確定申告の勘定科目名（未設定はF-23の集計で「雑費」として扱う）と有効フラグ
    tax_form_category VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT categories_user_id_name_key UNIQUE (user_id, name)
);
CREATE INDEX idx_categories_user_id ON categories(user_id);

CREATE TABLE expenses (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    title VARCHAR(100) NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    category_id INTEGER NOT NULL REFERENCES categories(id),
    expense_date DATE NOT NULL,
    memo TEXT,
    receipt_image_path VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'registered'
        CHECK (status IN ('registered', 'draft')),
    -- F-21：消費税区分。税抜金額・消費税額は列で持たず、税込金額と区分から算出する
    tax_category VARCHAR(20) NOT NULL DEFAULT 'taxable_10'
        CHECK (tax_category IN ('taxable_10', 'taxable_8', 'tax_exempt', 'non_taxable')),
    -- F-22：受領した領収書の適格請求書の判定。課税区分以外はnull
    is_qualified_invoice BOOLEAN,
    vendor_registration_number VARCHAR(14),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);
CREATE INDEX idx_expenses_expense_date ON expenses(expense_date);
CREATE INDEX idx_expenses_category_id ON expenses(category_id);
CREATE INDEX idx_expenses_deleted_at ON expenses(deleted_at);
CREATE INDEX idx_expenses_user_id ON expenses(user_id);

CREATE TABLE password_reset_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE business_profiles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    business_name VARCHAR(100),
    owner_name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    invoice_registration_number VARCHAR(14),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE clients (
    id              SERIAL       PRIMARY KEY,
    user_id         INTEGER      NOT NULL REFERENCES users(id),
    name            VARCHAR(100) NOT NULL,
    honorific       VARCHAR(10)  NOT NULL DEFAULT '御中',
    contact_person  VARCHAR(100),
    address         VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(20),
    memo            TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_clients_user_id ON clients(user_id);
CREATE INDEX idx_clients_is_active ON clients(is_active);

-- F-17（請求書作成）で追加。本番の13〜15のマイグレーションSQLと同じ定義
CREATE TABLE invoices (
    id                                 SERIAL       PRIMARY KEY,
    user_id                            INTEGER      NOT NULL REFERENCES users(id),
    client_id                          INTEGER      NOT NULL REFERENCES clients(id),
    invoice_number                     VARCHAR(20),
    issue_date                         DATE         NOT NULL,
    due_date                           DATE         NOT NULL,
    status                             VARCHAR(20)  NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'issued', 'canceled')),
    subtotal_amount                    INTEGER      NOT NULL DEFAULT 0 CHECK (subtotal_amount >= 0),
    tax_amount                         INTEGER      NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    total_amount                       INTEGER      NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    client_name                        VARCHAR(100),
    client_honorific                   VARCHAR(10),
    client_address                     VARCHAR(255),
    issuer_business_name               VARCHAR(100),
    issuer_owner_name                  VARCHAR(100),
    issuer_address                     VARCHAR(255),
    issuer_invoice_registration_number VARCHAR(14),
    payment_status                     VARCHAR(20)  NOT NULL DEFAULT 'unpaid'
        CHECK (payment_status IN ('unpaid', 'paid')),
    paid_at                            TIMESTAMP,
    issued_at                          TIMESTAMP,
    canceled_at                        TIMESTAMP,
    canceled_reason                    VARCHAR(255),
    created_at                         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT invoices_user_id_invoice_number_key UNIQUE (user_id, invoice_number)
);
CREATE INDEX idx_invoices_user_id ON invoices(user_id);
CREATE INDEX idx_invoices_issue_date ON invoices(issue_date);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_client_id ON invoices(client_id);

CREATE TABLE invoice_items (
    id            SERIAL         PRIMARY KEY,
    invoice_id    INTEGER        NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    display_order INTEGER        NOT NULL DEFAULT 0,
    description   VARCHAR(100)   NOT NULL,
    quantity      DECIMAL(10,2)  NOT NULL CHECK (quantity > 0),
    unit_price    INTEGER        NOT NULL CHECK (unit_price >= 0),
    tax_category  VARCHAR(20)    NOT NULL DEFAULT 'taxable_10'
        CHECK (tax_category IN ('taxable_10', 'taxable_8', 'tax_exempt')),
    tax_rate      INTEGER        NOT NULL DEFAULT 10 CHECK (tax_rate IN (10, 8, 0)),
    amount        INTEGER        NOT NULL CHECK (amount >= 0)
);
CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);

CREATE TABLE invoice_number_sequences (
    user_id     INTEGER   NOT NULL REFERENCES users(id),
    year        INTEGER   NOT NULL,
    last_number INTEGER   NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, year)
);