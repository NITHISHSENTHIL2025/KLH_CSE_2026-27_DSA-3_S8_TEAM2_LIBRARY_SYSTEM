-- Complete the request lifecycle so the request record reflects a returned loan.
ALTER TABLE borrow_requests
    DROP CONSTRAINT IF EXISTS borrow_requests_status_check;

ALTER TABLE borrow_requests
    ADD CONSTRAINT borrow_requests_status_check
    CHECK (status IN ('REQUESTED','APPROVED','REJECTED','CANCELLED','ISSUED','RETURNED','EXPIRED'));

CREATE INDEX IF NOT EXISTS idx_requests_book_status
    ON borrow_requests(book_id,status,expires_at);
