-- F-22 適格請求書チェック
--
-- 受領した領収書が適格請求書（インボイス）の要件を満たすかを記録する。
-- F-18で扱う「適格請求書」は自分が発行する側の書類を指すため、
-- 受領側であることが分かるよう列名に vendor（支払先）を付けている。

-- 課税区分（taxable_10 / taxable_8）以外はnullとするためNULL許容にする。
-- 「非課税・不課税だから対象外」と「課税だが未判定」を区別できるようにするため、
-- DEFAULT falseは付けない。
ALTER TABLE expenses ADD COLUMN is_qualified_invoice BOOLEAN;

-- インボイス登録番号は「T＋数字13桁」で14文字。
-- F-15で作成した business_profiles.invoice_registration_number と同じ長さに揃える
-- （要件7/11版のVARCHAR(13)では「T」の分が足りない）。
ALTER TABLE expenses ADD COLUMN vendor_registration_number VARCHAR(14);

-- 事後確認用
-- SELECT COUNT(*) FROM expenses WHERE is_qualified_invoice IS NOT NULL;
