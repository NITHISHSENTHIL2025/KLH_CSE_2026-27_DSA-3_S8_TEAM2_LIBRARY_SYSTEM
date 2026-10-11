CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  student_code VARCHAR(64) UNIQUE,
  name VARCHAR(140) NOT NULL,
  email VARCHAR(180),
  department VARCHAR(120),
  study_year VARCHAR(40),
  role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','ADMIN')),
  password_hash VARCHAR(100),
  card_hash VARCHAR(128) UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sessions (
  id BIGSERIAL PRIMARY KEY,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workstation_id VARCHAR(100),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS books (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(320) NOT NULL,
  author VARCHAR(280) NOT NULL,
  isbn VARCHAR(40) UNIQUE,
  category VARCHAR(120) NOT NULL,
  keywords TEXT,
  resource_type VARCHAR(40) NOT NULL DEFAULT 'BOOK',
  publisher VARCHAR(180),
  publication_year INT,
  description TEXT,
  language VARCHAR(40) NOT NULL DEFAULT 'English',
  rating NUMERIC(3,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS book_copies (
  id BIGSERIAL PRIMARY KEY,
  book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
  barcode VARCHAR(100) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','ISSUED','LOST','ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS borrow_requests (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  book_id BIGINT NOT NULL REFERENCES books(id),
  status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','APPROVED','REJECTED','CANCELLED','ISSUED','EXPIRED')),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  decision_at TIMESTAMPTZ,
  decision_by BIGINT REFERENCES users(id),
  expires_at TIMESTAMPTZ,
  note VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS loans (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  book_id BIGINT NOT NULL REFERENCES books(id),
  copy_id BIGINT NOT NULL REFERENCES book_copies(id),
  request_id BIGINT REFERENCES borrow_requests(id),
  issued_by BIGINT REFERENCES users(id),
  returned_by BIGINT REFERENCES users(id),
  issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  due_at TIMESTAMPTZ NOT NULL,
  returned_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED','RETURNED','OVERDUE'))
);

CREATE TABLE IF NOT EXISTS search_history (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  query VARCHAR(320) NOT NULL,
  result_count INT NOT NULL DEFAULT 0,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(180) NOT NULL,
  message VARCHAR(600) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  read_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  workstation_id VARCHAR(100),
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80),
  entity_id VARCHAR(100),
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_books_category ON books(category);
CREATE INDEX IF NOT EXISTS idx_books_author ON books(author);
CREATE INDEX IF NOT EXISTS idx_copies_book_status ON book_copies(book_id,status);
CREATE INDEX IF NOT EXISTS idx_requests_status ON borrow_requests(status,requested_at);
CREATE INDEX IF NOT EXISTS idx_requests_user ON borrow_requests(user_id,requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_loans_user_status ON loans(user_id,status);
CREATE INDEX IF NOT EXISTS idx_search_user ON search_history(user_id,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_request_per_book_user ON borrow_requests(user_id,book_id)
WHERE status IN ('REQUESTED','APPROVED');
