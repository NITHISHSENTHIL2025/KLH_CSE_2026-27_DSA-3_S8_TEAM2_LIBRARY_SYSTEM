# IntelliLib — Simple Explanation for Presentation / Viva

## Main motive

IntelliLib is a live digital library desktop application for finding and borrowing real physical library resources quickly.

Instead of manually checking shelves or searching records one by one, a student scans their registered ID card, searches the library, requests a resource, and receives the librarian's approval live. At collection time the administrator scans the student's ID and the exact physical book barcode. The same scan flow is used during return.

## Student flow

**Scan ID → Home → Search → Open Book → Request → Wait for Approval → Collect → My Books → Return**

The student's home shows two useful recommendation areas:

- **Popular in the library** — calculated from actual successful loans.
- **Recommended for you** — based only on that student's own views and borrowing history. If there is not enough history, IntelliLib shows an honest empty state instead of inventing personalization.

## Administrator flow

**Login → Dashboard → Requests → Approve/Reject → Issue / Return → Manage Books/Students → Loans/Activity**

For issuing:

1. Click Issue Book.
2. IntelliLib opens its in-app camera scanner.
3. Scan student ID.
4. Server verifies the student and approved requests.
5. Scan the exact physical book barcode.
6. Server verifies the copy and request.
7. Loan is created and copy becomes ISSUED in one database transaction.
8. Student receives a live notification.

Return is the same idea in reverse.

## Why it is live

All desktop apps connect to one cloud backend. Render hosts the Spring Boot API and WebSocket server. Neon stores one central PostgreSQL database. Therefore Student PC and Admin PC always work on the same data.

The user's laptop is not the server in production. It can be turned off.

## What is real and what is not

Production IntelliLib does not auto-load generated books or fake metrics. The administrator imports the real library/student information. Dashboard numbers are SQL counts from the live database.

## Why Docker exists

Docker is only a deployment/development tool for the server. Students and administrators never interact with it. Packaged desktop PCs run only IntelliLib.exe.
