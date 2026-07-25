-- ① まずNULL許容で追加
ALTER TABLE categories ADD COLUMN updated_at TIMESTAMP;

-- ② 既存データには、作成日時(created_at)と同じ値を暫定的に入れておく
UPDATE categories SET updated_at = created_at WHERE updated_at IS NULL;

-- ③ 全件に値が入ったことを確認した上で、NOT NULL制約を付与する
ALTER TABLE categories ALTER COLUMN updated_at SET NOT NULL;
