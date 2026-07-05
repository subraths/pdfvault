# PdfVault Backend

AWS Lambda + MongoDB backend for PdfVault. It adds **user accounts**, **multi-account S3 profiles that
sync across devices**, and **cross-device recents sync** (including reading progress) so the Android app
and the Linux desktop app share the same state.

The apps still talk to **S3 directly** with the user's own credentials — this backend only stores the
account metadata and recents, it does not proxy file traffic.

## Stack

- **AWS Lambda** (Node.js 20, TypeScript) behind **API Gateway HTTP API**
- **MongoDB Atlas** (connection cached across warm invocations)
- **Serverless Framework** for packaging + deploy (`serverless-esbuild` bundles the TypeScript)
- Auth: email/password (bcrypt) → **JWT** bearer tokens
- S3 secret keys are **encrypted at rest** with AES-256-GCM

## Layout

```
src/
  lib/         db (Mongo cache), http (responses + wrap), auth (JWT/bcrypt), crypto (AES-GCM), validation
  models/      types.ts (Mongo docs + client-facing views)
  handlers/    auth.ts, accounts.ts, recents.ts   (one Lambda per resource, internal method routing)
```

Collections: `users`, `profiles` (S3 accounts), `recents`. Indexes are created on first connection
(`users.email` unique, `recents.{userId,docId}` unique).

## Prerequisites

1. A **MongoDB Atlas** cluster + connection string (free M0 tier is fine).
2. AWS credentials configured locally (`aws configure`) for `serverless deploy`.
3. Node 20+.

## Setup & deploy

```bash
cd backend
npm install
cp .env.example .env      # fill in MONGODB_URI, JWT_SECRET, ENCRYPTION_KEY
# ENCRYPTION_KEY must be base64 of 32 bytes:  openssl rand -base64 32
# JWT_SECRET: any long random string:         openssl rand -hex 32

npm run typecheck         # tsc --noEmit
export $(grep -v '^#' .env | xargs)   # or use serverless-dotenv-plugin / direnv / CI secrets
npm run deploy            # serverless deploy  -> prints the base URL
```

Local run against Atlas: `npm run offline` (serverless-offline on http://localhost:3000).

## API

Base URL is whatever `serverless deploy` prints. All bodies are JSON. Authenticated routes need
`Authorization: Bearer <token>`.

### Auth

| Method | Path              | Body                          | Returns |
|--------|-------------------|-------------------------------|---------|
| POST   | `/auth/register`  | `{ email, password }`         | `{ token, user:{id,email} }` (201) |
| POST   | `/auth/login`     | `{ email, password }`         | `{ token, user:{id,email} }` |
| GET    | `/me`             | —                             | `{ id, email }` |

`password` must be ≥ 8 chars. Duplicate email → 409. Bad credentials → 401.

### Accounts (multi-account S3 profiles)

| Method | Path             | Body                                                        | Returns |
|--------|------------------|------------------------------------------------------------|---------|
| GET    | `/accounts`      | —                                                          | `{ accounts: Account[] }` |
| POST   | `/accounts`      | `{ name, region, bucket, accessKeyId, secretAccessKey, active? }` | `Account` (201) |
| PATCH  | `/accounts/{id}` | `{ name?, active? }`                                        | `Account` |
| DELETE | `/accounts/{id}` | —                                                          | 204 |

`Account = { id, name, region, bucket, accessKeyId, secretAccessKey, active, updatedAt }`.
The first account created is `active`; setting `active:true` (on create or PATCH) makes it the sole
active one. Deleting the active account promotes the newest remaining one. `secretAccessKey` is
returned **decrypted** to the authenticated owner (over HTTPS) so clients can talk to S3.

### Recents (synced, includes reading progress)

| Method | Path                       | Body                                                   | Returns |
|--------|----------------------------|--------------------------------------------------------|---------|
| GET    | `/recents`                 | —                                                      | `{ recents: Recent[] }` |
| PUT    | `/recents`                 | `{ docId, name, openedAt, totalPages, lastPage, updatedAt }` | `{ recent }` |
| POST   | `/recents/sync`            | `{ items: Recent[] }`                                  | `{ recents: Recent[] }` (merged) |
| DELETE | `/recents?docId=<key>`     | —                                                      | 204 |

`Recent = { docId, name, openedAt, totalPages, lastPage, updatedAt }` where `docId` is the S3 object
key (or `local:<path>` for a desktop-local file), and `updatedAt`/`openedAt` are epoch millis.

**Sync semantics:** `/recents/sync` merges the client's list into the server's per-document by
**last-write-wins on `updatedAt`**, then returns the full merged set. A client should: send its local
recents, replace its local list with the response, and record the sync time. `PUT /recents` is an
unconditional single upsert for immediate updates (e.g. right after opening a PDF or turning a page).
`DELETE` takes `docId` as a **query parameter** (S3 keys contain `/`, so it can't be a path segment).

## Security notes

- S3 secret keys are encrypted at rest (AES-256-GCM, key from `ENCRYPTION_KEY`) and only decrypted for
  the owning user. Rotating `ENCRYPTION_KEY` invalidates stored secrets (users would re-enter them).
- Everything is over HTTPS (API Gateway). Use a strong `JWT_SECRET`; tokens default to a 30-day TTL.
- Consider adding rate limiting (API Gateway throttling / usage plans) and Atlas IP allow-listing before
  production use.

## Client wiring (next phase — not yet implemented in the apps)

The Android app and desktop app currently store credentials + recents **locally**. To adopt this backend:

1. Add a sign-in screen (register/login) and keep the JWT (Android: EncryptedSharedPreferences; desktop:
   the existing properties store).
2. On sign-in, `GET /accounts` → seed the local profile store; push local-only profiles with `POST /accounts`.
3. On open/close of a PDF and on app start, `POST /recents/sync` with the local list and adopt the merged
   result; keep `PUT /recents` for live progress updates.
4. Treat the backend as the source of truth when signed in; fall back to local-only when signed out/offline.
