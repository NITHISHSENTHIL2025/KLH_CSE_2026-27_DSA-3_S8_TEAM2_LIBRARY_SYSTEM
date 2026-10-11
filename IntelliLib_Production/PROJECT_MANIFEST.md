# Project Manifest

## Product

IntelliLib production remake, version 2.0.

Two roles only:

- Student
- Administrator

## Main modules

- `client/` — Java 21 + JavaFX Windows desktop application.
- `server/` — Spring Boot API + WebSocket + search engine.
- `server/src/main/resources/db/migration/` — PostgreSQL/Flyway schema.
- `deployment/render.yaml` — Render Blueprint configuration.
- `data/templates/` — header-only real-data CSV templates.
- `scripts/` — Windows run/build/configuration helpers.
- `docs/` — simple explanation, DSA usage, security, imports, tests, Render/Neon deployment.
- `presentation/` — final production PowerPoint.

## Deliberately removed from the previous prototype

- Third “Library Counter” role.
- Generated 10,000-book dataset as production content.
- Demo student-card tokens as normal user flow.
- Native Java book dialogs as the intended detail experience.
- Fake dashboard metrics.
- Local-PC-as-production-server assumption.

## Production behavior

A clean production database bootstraps only the administrator. Real students, books and physical copy barcodes are imported/enrolled by the administrator.
