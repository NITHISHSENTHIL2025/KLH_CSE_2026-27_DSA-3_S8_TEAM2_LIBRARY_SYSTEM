# Production Test Plan

## Identity

- Registered real card logs in correct student.
- Unknown card is rejected.
- Disabled student card is rejected.
- Camera denied by Windows gives a clean in-app error, not a crash.
- Camera already used by another app gives retry guidance.

## Search

- Exact ISBN returns correct resource.
- Title/author/category/keyword search returns real imported records.
- Prefix suggestions work after import.
- Small spelling error can recover a relevant result.
- Empty database shows a real empty state, not fabricated content.

## Requests

- Available book can be requested.
- Duplicate active request is rejected.
- No-copy book cannot be requested.
- Admin approval creates student notification.
- Admin cannot over-approve beyond unreserved available copies.

## Issue

- Student scan is verified first.
- Issue refuses a book not approved for that student.
- Issue refuses an already-issued copy.
- Successful issue updates copy + request + loan atomically.
- Student receives live issue notification.

## Return

- Return rejects a book not loaned to that student.
- Double return is rejected.
- Successful return restores exact physical copy availability.

## Cloud

- Desktop connects using Render HTTPS URL.
- WebSocket notification arrives on a different PC/network.
- Neon retains data after Render restart/redeploy.
- Laptop running the desktop client can be powered off without taking cloud backend down.
