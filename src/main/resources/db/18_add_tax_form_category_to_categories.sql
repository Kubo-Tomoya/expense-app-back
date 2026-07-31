-- F-28 カテゴリ管理
--
-- 7/11版のF-14では「categoriesに tax_form_category（確定申告の勘定科目名）を追加する」
-- と定義していたが、Phase A実装時に漏れていた。F-23（年間レポートの勘定科目別集計）が
-- この列に依存するため、本機能で追加する。

-- 確定申告の勘定科目名（例：旅費交通費、水道光熱費）。
-- 業種固有の科目もあるため選択肢に限定せず、自由入力も許容するためVARCHAR。
-- 未設定（NULL）の場合、F-23の集計では「雑費」として扱う。
-- F-25（freee連携）では勘定科目マッピングのキーとしても兼用する。
ALTER TABLE categories ADD COLUMN tax_form_category VARCHAR(50);

-- 有効フラグ。取引先（clients）と同じ無効化方式を採る。
-- 過去の経費から参照され続けるため物理削除は行わず、
-- falseにすると経費登録時のプルダウンから外れる。
-- DEFAULT true のため既存データの移行作業は不要。
ALTER TABLE categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- 既存ユーザーのデフォルトカテゴリに、代表的な勘定科目を割り当てる。
-- F-14で自動作成される5件（交通費／食費／通信費／消耗品費／その他）が対象。
-- ユーザーが独自に作成・改名したカテゴリには影響しない（名前が一致するものだけ更新する）。
UPDATE categories SET tax_form_category = '旅費交通費' WHERE name = '交通費' AND tax_form_category IS NULL;
UPDATE categories SET tax_form_category = '接待交際費' WHERE name = '食費' AND tax_form_category IS NULL;
UPDATE categories SET tax_form_category = '通信費'     WHERE name = '通信費' AND tax_form_category IS NULL;
UPDATE categories SET tax_form_category = '消耗品費'   WHERE name = '消耗品費' AND tax_form_category IS NULL;
UPDATE categories SET tax_form_category = '雑費'       WHERE name = 'その他' AND tax_form_category IS NULL;

-- 事後確認用
-- SELECT name, tax_form_category, is_active FROM categories ORDER BY user_id, display_order;
