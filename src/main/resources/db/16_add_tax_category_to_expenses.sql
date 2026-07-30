-- F-21 消費税区分の追加
--
-- 金額（amount）は従来どおり税込で保持し、税抜金額・消費税額は列として持たない。
-- 必要な箇所（F-23の年間レポート）で税込金額と区分から算出する。
-- F-20で集計金額を税込に統一したため、同じ金額を2系統で管理しないようにする方針。

-- 既存データはDEFAULTで 'taxable_10'（課税10%）として移行される。
-- 実際には軽減8%・非課税の経費が含まれている可能性があるため、
-- 移行後にS-10の詳細検索で区分を絞り込んで見直せるようにしている。
ALTER TABLE expenses ADD COLUMN tax_category VARCHAR(20) NOT NULL DEFAULT 'taxable_10';

-- 意図しない文字列が入らないようCHECK制約を追加する。
-- ENUM型ではなくVARCHAR＋CHECKとするのは、区分を追加する際にALTER TYPEを避けるため
-- （statusで採用したのと同じ方式）。
ALTER TABLE expenses ADD CONSTRAINT chk_expenses_tax_category
  CHECK (tax_category IN ('taxable_10', 'taxable_8', 'tax_exempt', 'non_taxable'));

-- 事後確認用（すべて taxable_10 になっていることを確認する）
-- SELECT tax_category, COUNT(*) FROM expenses GROUP BY tax_category;
