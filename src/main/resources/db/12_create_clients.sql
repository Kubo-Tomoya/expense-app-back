-- 取引先情報を管理するテーブル。F-16（取引先管理）で新規作成
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

-- user_id・is_activeでの絞り込みが頻発するため、検索性能のためインデックスを追加する
CREATE INDEX idx_clients_user_id ON clients(user_id);
CREATE INDEX idx_clients_is_active ON clients(is_active);