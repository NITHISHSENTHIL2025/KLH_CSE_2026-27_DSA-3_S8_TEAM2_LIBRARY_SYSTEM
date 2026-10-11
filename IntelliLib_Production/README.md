# IntelliLib — Production Desktop Library System

IntelliLib is a two-role desktop library application built for a real physical library workflow.

**Roles:** Student and Administrator only.

**Live architecture:** JavaFX desktop app → HTTPS/WebSocket → Spring Boot API on Render → Neon PostgreSQL.

The desktop application never contains fake production metrics or generated production books. A new production database contains only the administrator account until real students/books are imported.

## What students do

1. Open IntelliLib.
2. Choose **Student**.
3. IntelliLib opens an in-app camera scanner.
4. Show the QR/barcode on the registered college ID card.
5. Search by title, author, ISBN, category, or keyword.
6. View real availability and related books.
7. Request a book.
8. Receive live approval/issue/return notifications.
9. Track requests and currently borrowed physical copies.

## What administrators do

- Secure email/password sign-in.
- See live database counts only: books, physical copies, available copies, students, issued books, pending requests, overdue books.
- Import real book/student/copy CSV files.
- Approve/reject borrow requests.
- Issue: scan student ID → scan exact physical copy → system validates and creates loan atomically.
- Return: scan student ID → scan physical copy → system validates and returns it atomically.
- Register a student by scanning the real ID card inside IntelliLib.
- Search real library metadata and add a physical copy by scanning its barcode.
- Review loans and audit activity.

## Camera scanning

The scanner is part of IntelliLib itself. It uses the selected Windows webcam and ZXing to read QR, Code 128/39/93, EAN, UPC, ITF, Codabar, Data Matrix, and PDF417 codes. No separate camera application is opened.

The code stored on a real student ID must be imported/enrolled as `card_code`. IntelliLib stores only a keyed hash of that card code on the server. Physical-book barcodes are stored as exact copy identifiers.

## Production data

Use the templates in `data/templates/`:

- `students_template.csv`
- `books_template.csv`
- `book_copies_template.csv`

`books_template.csv` may optionally include a `barcode` on each row. If one ISBN has multiple physical copies, `book_copies_template.csv` is the cleanest format because each physical copy gets its own barcode.

## Run locally for development

Requirements on the development PC only:

- JDK 21
- Docker Desktop

From the project root:

```bat
scripts\START_SERVER_WINDOWS.bat
scripts\RUN_CLIENT_WINDOWS.bat
```

Local Docker exists only to make development easy. **End-user PCs do not need Docker.**

## Deploy live with Render + Neon

Read `docs/RENDER_NEON_DEPLOYMENT.md`.

In production, Render runs the Spring Boot backend and Neon stores the PostgreSQL data. Your own laptop can be shut down; the cloud system remains available. Render Free may sleep after inactivity, so the first request after a sleep can take longer to wake. This is suitable for a zero-cost project/pilot, not a guaranteed always-on institutional SLA.

## Build the Windows desktop app

On a Windows development PC with JDK 21:

```bat
scripts\BUILD_WINDOWS_PORTABLE.bat
```

Output:

```text
client\dist\IntelliLib\IntelliLib.exe
```

The portable app-image contains its own Java runtime. Other student/admin PCs do not need Java, Maven, Docker, PostgreSQL, or source code.

For a Windows installer, use `scripts\BUILD_WINDOWS_INSTALLER.bat`; some JDK setups require WiX for EXE installer generation.

## First production setup

1. Deploy backend to Render using Neon.
2. Set a strong `APP_CARD_PEPPER` and administrator password in Render secrets.
3. Build/copy IntelliLib to a Windows PC.
4. On IntelliLib landing page, open **Server settings** and save the Render URL.
5. Sign in as administrator.
6. Import real students and books.
7. Import/enroll exact physical copy barcodes.
8. Test one full request → approval → issue → return flow.

## Project documentation

- `docs/SIMPLE_EXPLANATION.md` — easy viva explanation.
- `docs/DSA_USAGE.md` — where the data structures/algorithms are used and why.
- `docs/RENDER_NEON_DEPLOYMENT.md` — cloud setup.
- `docs/REAL_DATA_IMPORT.md` — CSV fields and import workflow.
- `docs/SECURITY.md` — security model and limits of barcode-only identity.
- `docs/TEST_PLAN.md` — real end-to-end test cases.
- `presentation/IntelliLib_Final_Production_Presentation.pptx` — presentation deck.
