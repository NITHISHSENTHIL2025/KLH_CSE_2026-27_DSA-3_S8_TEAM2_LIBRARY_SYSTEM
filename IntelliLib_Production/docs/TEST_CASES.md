# Test Cases

Canonical test coverage is in `TEST_PLAN.md`.

Minimum release gate:

- real camera can decode at least one registered student card on the target laptop;
- real camera can decode the chosen physical-book barcode format;
- student login works from cloud server;
- search, request and admin approval work;
- WebSocket notification arrives;
- wrong/unapproved book cannot be issued;
- exact approved copy can be issued once only;
- return succeeds once and rejects a double return;
- Neon retains records after a Render restart;
- no generated production books/metrics appear in a clean deployment.
