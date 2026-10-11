# Security Design

- Admin passwords use BCrypt hashes; plaintext passwords are never stored in PostgreSQL.
- Student card codes are transformed with HMAC-SHA256 using `APP_CARD_PEPPER`; raw card codes are not stored by the server.
- Desktop sessions use random tokens; only SHA-256 hashes of session tokens are stored.
- Admin-only endpoints verify an administrator session on every request.
- SQL values use JdbcTemplate parameters instead of string-concatenating user input.
- Issue/return operations are transactional and lock the relevant rows to prevent double issuing.
- Every physical copy has a unique barcode and state: AVAILABLE, ISSUED, LOST, or ARCHIVED.
- Important admin/circulation actions go to the audit log.
- Neon credentials stay only in Render environment secrets.
- Client PCs connect to Render over HTTPS/WSS in production.

## Important limitation of normal QR/barcode ID cards

A visible barcode/QR can potentially be copied. A scan proves possession of a code, not biometric identity. For a college library counter this should be combined with normal physical verification (student photo/name on the ID, administrator present during issue). Do not describe ordinary printed barcodes as cryptographically unclonable.
