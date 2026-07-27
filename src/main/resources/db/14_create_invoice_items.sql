-- 請求書明細を管理するテーブル。F-17（請求書作成）で新規作成
--
-- 設計上のポイント：
-- ・更新は全行差し替え方式（部分更新は行わない）。並び順はdisplay_orderで保持する
-- ・ON DELETE CASCADEは、下書きの請求書を削除した際に明細を残さないため
-- ・tax_categoryとtax_rateの両方を持つ理由：区分名はF-21/F-23とのマッピングに使い、
--   tax_rateは発行時点の税率をスナップショットとして残す（将来の税率改定時に
--   過去の請求書の金額が変わらないようにするため）
-- ・消費税は明細単位では計算しない。tax_categoryごとにamountを合計した後、
--   1回だけ税率を掛けて1円未満を切り捨てる（適格請求書の「税率ごとに1回の端数処理」要件）
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

-- 親請求書から明細を取得する経路が主なアクセスパターンのため
CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);
