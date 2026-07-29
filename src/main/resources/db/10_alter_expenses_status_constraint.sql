-- 事前確認用（制約追加前に実行し、0件であることを確認してください）
SELECT DISTINCT status FROM expenses WHERE status NOT IN ('registered', 'draft');

-- statusカラムに、意図しない文字列が入らないようCHECK制約を追加する
ALTER TABLE expenses ADD CONSTRAINT chk_expenses_status
  CHECK (status IN ('registered', 'draft'));
