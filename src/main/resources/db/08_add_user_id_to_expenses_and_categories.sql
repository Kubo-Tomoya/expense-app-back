-- ① まずNULL許容でuser_id列を追加する
ALTER TABLE categories ADD COLUMN user_id INTEGER REFERENCES users(id);
ALTER TABLE expenses ADD COLUMN user_id INTEGER REFERENCES users(id);

-- ② 既存の全データを、MVP開発で使用してきたテストアカウント（id=1）に割り当てる
--    ⚠️ この値は当時のDB状態に基づく決め打ちです。
--    別環境で実行する場合は、事前に SELECT * FROM users; で現状を確認し、
--    どのユーザーに割り当てるべきか再確認してください。
UPDATE categories SET user_id = 1 WHERE user_id IS NULL;
UPDATE expenses SET user_id = 1 WHERE user_id IS NULL;

-- ③ 全件に値が入ったことを確認した上で、NOT NULL制約を付与する
ALTER TABLE categories ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE expenses ALTER COLUMN user_id SET NOT NULL;

-- ④ カテゴリの一意制約を「name単独」から「user_id + name」に変更する
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_name_key;
ALTER TABLE categories ADD CONSTRAINT categories_user_id_name_key UNIQUE (user_id, name);

-- ⑤ 検索性能のためインデックスを追加する
CREATE INDEX idx_expenses_user_id ON expenses(user_id);
CREATE INDEX idx_categories_user_id ON categories(user_id);
