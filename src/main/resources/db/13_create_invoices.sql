-- 請求書を管理するテーブル。F-17（請求書作成）で新規作成
--
-- 設計上のポイント：
-- ・invoice_numberは下書き（draft）の間はNULL。発行時に採番する
--   （取消の多いdraftで番号が歯抜けになるのを防ぐため）
-- ・一意制約はUNIQUE(user_id, invoice_number)の複合制約。単独UNIQUEにすると、
--   あるユーザーがINV-2026-0001を発行済みの場合に他ユーザーが採番できなくなる
--   （F-14でcategoriesの一意制約をuser_id+nameに変更したのと同じ設計思想）
-- ・client_*・issuer_*は発行時点のスナップショット。取引先の改名や事業者プロフィールの
--   変更があっても、発行済み請求書の記載内容が遡って変わらないようにするため
-- ・payment_status・paid_atはF-19（入金管理）で使用する列だが、後からALTER TABLEしないよう
--   本テーブル作成時に併せて作成する
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

-- user_idでの絞り込みは全APIで必須（F-14の設計方針）
CREATE INDEX idx_invoices_user_id ON invoices(user_id);
-- 年別表示・期間別集計（F-20）で発行日を条件にするため
CREATE INDEX idx_invoices_issue_date ON invoices(issue_date);
-- 発行済みのみの売上集計・未入金抽出（F-19/F-20）で使用するため
CREATE INDEX idx_invoices_status ON invoices(status);
-- 取引先別の抽出のため
CREATE INDEX idx_invoices_client_id ON invoices(client_id);
