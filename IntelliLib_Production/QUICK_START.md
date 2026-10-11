# IntelliLib Quick Start

## A. Fast local test on one Windows PC

1. Install **JDK 21** and **Docker Desktop**.
2. Start Docker Desktop.
3. From this project, run:

```bat
scripts\START_SERVER_WINDOWS.bat
```

4. Wait until it says the API is live on `http://localhost:8080`.
5. Run:

```bat
scripts\RUN_CLIENT_WINDOWS.bat
```

6. Choose **Administrator** and sign in with the local-development credentials defined in `docker-compose.yml`.
7. Import real/safe test CSVs using the templates in `data\templates`.
8. For student login, the student must have a `card_code` enrolled/imported and Windows must allow IntelliLib to access the camera.

Local Docker is for development only.

## B. Real cloud setup

1. Deploy `server/` to Render.
2. Put the Neon connection URL into Render `DATABASE_URL`.
3. Add strong Render secrets for `APP_CARD_PEPPER` and admin credentials.
4. Open IntelliLib → **Server settings** → paste your Render URL.
5. Build the portable desktop app with:

```bat
scripts\BUILD_WINDOWS_PORTABLE.bat
```

6. Copy `client\dist\IntelliLib` to Student/Admin Windows PCs.
7. They run only `IntelliLib.exe`; they do not need Docker, PostgreSQL, Maven, or a JDK.

See `docs\RENDER_NEON_DEPLOYMENT.md` for exact details.
