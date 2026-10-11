# Render + Neon Deployment — Zero-Cost Project Setup

This makes IntelliLib accessible even when your own laptop is shut down.

## Architecture

```text
IntelliLib.exe → HTTPS / WebSocket → Render Spring Boot API → Neon PostgreSQL
```

## 1. Neon

Create a PostgreSQL project and copy its connection URL. Do not place the real URL inside source files.

## 2. GitHub

Create a private/public repository containing this project. Render builds the `server/` folder.

## 3. Render Web Service

You can either use the included `render.yaml` Blueprint or create a Web Service manually.

Recommended settings:

- Runtime: Docker
- Root directory: `server`
- Dockerfile: `server/Dockerfile`
- Health check: `/api/health`
- Plan: Free for project/pilot

## 4. Render environment variables

Add these as secrets:

```text
DATABASE_URL=<your Neon connection URL>
APP_CARD_PEPPER=<long random secret, at least 32 characters>
APP_BOOTSTRAP_ADMIN_EMAIL=<your admin email>
APP_BOOTSTRAP_ADMIN_PASSWORD=<strong password, 12+ characters>
APP_DEFAULT_DUE_DAYS=14
```

Do not set `PORT`; Render supplies it.

The server understands both Neon `postgresql://...` URLs and JDBC `jdbc:postgresql://...` URLs.

## 5. Deploy

Wait for Render to report Healthy. Open:

```text
https://YOUR-SERVICE.onrender.com/api/health
```

A successful response confirms the API is live.

## 6. Connect IntelliLib desktop

Open IntelliLib → **Server settings** → enter:

```text
https://YOUR-SERVICE.onrender.com
```

Save and test.

You can also use:

```bat
scripts\SET_CLOUD_SERVER_WINDOWS.bat
```

## 7. Import real data

Sign in as Administrator and import students/books/copies. Flyway automatically creates the Neon schema on first boot.

## Render Free limitation

A free Render service may sleep after inactivity and need a cold-start on the next request. The desktop app should be considered a zero-cost pilot/project deployment, not a guaranteed 24/7 institutional SLA. Neon persists the database independently.
