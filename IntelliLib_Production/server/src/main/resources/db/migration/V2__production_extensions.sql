CREATE TABLE IF NOT EXISTS book_views (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
  viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_book_views_user_time ON book_views(user_id, viewed_at DESC);
CREATE INDEX IF NOT EXISTS idx_book_views_book_time ON book_views(book_id, viewed_at DESC);
CREATE INDEX IF NOT EXISTS idx_loans_book_time ON loans(book_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_time ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log(created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_loan_per_copy ON loans(copy_id) WHERE returned_at IS NULL;
