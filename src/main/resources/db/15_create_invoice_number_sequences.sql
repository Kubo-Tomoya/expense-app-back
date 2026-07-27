-- 請求書番号の採番を管理するテーブル。F-17（請求書作成）で新規作成
--
-- 設計上のポイント：
-- ・採番は発行時（statusをissuedに変更するタイミング）のみ行う。下書きでは採番しない
-- ・yearは発行日（issue_date）の年を使う。発行操作日ではなく会計年度に合わせるため
-- ・同時実行で番号が重複しないよう、該当行を排他ロック（SELECT ... FOR UPDATE）してから
--   last_numberをインクリメントする（アプリ側は @Lock(PESSIMISTIC_WRITE)）
-- ・請求書番号の形式は INV-{year}-{last_numberを4桁ゼロ埋め}（例：INV-2026-0001）
-- ・取消（canceled）でもlast_numberは戻さない。番号を再利用すると、
--   同一番号の請求書が2枚存在することになり監査時に説明できないため
CREATE TABLE invoice_number_sequences (
    user_id     INTEGER   NOT NULL REFERENCES users(id),
    year        INTEGER   NOT NULL,
    last_number INTEGER   NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, year)
);
