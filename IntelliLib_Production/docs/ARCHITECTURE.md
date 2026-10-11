# Architecture

```text
Student/Admin IntelliLib.exe
        │
        ├── HTTPS REST requests
        └── WSS live notifications
        │
        ▼
Render — Spring Boot API
        │
        ├── Authentication / sessions
        ├── Borrow requests
        ├── Transactional issue / return
        ├── Search + DSA indexes
        ├── Recommendations
        └── WebSocket broker
        │
        ▼
Neon PostgreSQL
```

Only two application roles exist: `STUDENT` and `ADMIN`.

The desktop camera scanner is part of the JavaFX client. It decodes the real ID/book code locally, then sends only the decoded value over HTTPS for server validation.
