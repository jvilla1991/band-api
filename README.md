# band-api

Backend REST API for the Villxin band site. Built with Spring Boot 3, PostgreSQL, and deployed to AWS Elastic Beanstalk.

## Features

- **Mailing list** — email signup with duplicate detection
- **Auth** — JWT-based registration and login
- **Messageboards** — posts and replies, authenticated or anonymous
- **Merch shop** — product catalog, Stripe Checkout, and webhook order fulfillment

---

## Getting Started

### Prerequisites

- Java 21
- Maven
- Docker (for the local database)

### Run locally

```bash
# 1. Copy the env template and fill in your values
cp .env.example .env

# 2. Start PostgreSQL
docker compose up -d

# 3. Start the API (Flyway runs migrations automatically)
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

---

## Coding Best Practices

### 1. No credentials in source code

All secrets — database credentials, JWT secret, Stripe keys — are injected via environment variables at runtime. The application reads them through Spring's `${ENV_VAR:default}` syntax in `application.properties`. Defaults are safe for local development only and must be overridden in production.

```properties
jwt.secret=${JWT_SECRET:local-dev-secret-change-this-in-prod}
stripe.secret-key=${STRIPE_SECRET_KEY:sk_test_placeholder}
```

A `.env.example` file is committed to document every required variable. The actual `.env` is gitignored.

---

### 2. Schema managed by Flyway, not Hibernate

Hibernate's `ddl-auto` is set to `validate` — it confirms the schema matches the entities but never modifies it. All DDL lives in versioned Flyway migration scripts under `src/main/resources/db/migration/`.

```
V1__init_schema.sql   ← creates all tables
```

This means schema changes are explicit, reviewable, and safe to run in production without surprises.

---

### 3. Admin seeding without hardcoded hashes

The initial admin user is created at startup by `DataSeeder` using credentials from `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables. The password is BCrypted at runtime — no pre-computed hash is ever stored in source or config files. The seeder is idempotent: it checks for the user before inserting.

---

### 4. Stateless authentication with JWT

The API has no server-side session state. Every protected request carries a signed JWT in the `Authorization: Bearer <token>` header. `JwtAuthFilter` validates and parses the token on each request, populating the Spring Security context without hitting the database.

This makes the service horizontally scalable — any instance can handle any request.

---

### 5. Role-based access control

Spring Security rules are declared centrally in `SecurityConfig` and applied at the URL level before any controller code runs:

```java
.requestMatchers(HttpMethod.DELETE, "/api/posts/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.POST,   "/api/shop/checkout").permitAll()
```

Controllers do not contain auth logic. New endpoints default to `authenticated()` unless explicitly opened, which makes accidental exposure harder.

---

### 6. Domain-packaged shop for future SOA extraction

The shop feature lives entirely under `com.villxin.bandapi.shop`, with its own entities, repositories, and controllers. Nothing outside this package depends on it. When the shop grows large enough to warrant its own service, the package can be lifted out with minimal friction.

---

### 7. Price locked at order time

`OrderItem` stores `unitPrice` copied from the product at the moment of checkout. Product prices can change freely without corrupting historical order totals or receipts.

---

### 8. Stripe webhook signature verification

The webhook endpoint verifies every incoming event against the `STRIPE_WEBHOOK_SECRET` before processing it. Requests with an invalid or missing signature are rejected with `400`. This prevents bad actors from spoofing payment confirmations.

```java
Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
```

---

### 9. Pagination on list endpoints

List endpoints for posts and products use Spring Data's `Pageable` with a server-side cap on page size (`Math.min(size, 50)`). Clients cannot request unbounded result sets.

---

### 10. Anonymous and authenticated posts coexist cleanly

The `Authentication` object from Spring Security is `null` for unauthenticated requests. The board controller handles both cases in one place without branching into separate endpoints:

```java
if (auth != null && auth.isAuthenticated()) {
    post.setAuthorEmail(auth.getName());
} else {
    post.setAuthorName(request.displayName() != null ? request.displayName() : "Anonymous");
}
```

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `DB_URL` | Yes | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | Database user |
| `DB_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | HMAC signing key, min 32 chars |
| `CORS_ORIGINS` | Yes | Comma-separated allowed frontend origins |
| `ADMIN_EMAIL` | First run | Email for the seeded admin user |
| `ADMIN_PASSWORD` | First run | Password for the seeded admin user |
| `STRIPE_SECRET_KEY` | Yes | Stripe secret key (`sk_live_` or `sk_test_`) |
| `STRIPE_WEBHOOK_SECRET` | Yes | Stripe webhook signing secret |
| `STRIPE_SUCCESS_URL` | Yes | Redirect URL after successful payment |
| `STRIPE_CANCEL_URL` | Yes | Redirect URL on cancelled payment |

---

## API Reference

### Auth
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create account, returns JWT |
| POST | `/api/auth/login` | Public | Login, returns JWT |

### Mailing List
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/subscribe` | Public | Subscribe an email address |

### Messageboards
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/posts` | Public | Paginated post list |
| GET | `/api/posts/{id}` | Public | Single post with replies |
| POST | `/api/posts` | Public | Create post (JWT optional) |
| POST | `/api/posts/{id}/replies` | Public | Add reply (JWT optional) |
| DELETE | `/api/posts/{id}` | Admin | Delete post |
| DELETE | `/api/posts/{postId}/replies/{replyId}` | Admin | Delete reply |

### Shop
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/shop/products` | Public | Paginated active products |
| GET | `/api/shop/products/{id}` | Public | Single product |
| POST | `/api/shop/products` | Admin | Create product |
| PUT | `/api/shop/products/{id}` | Admin | Update product |
| DELETE | `/api/shop/products/{id}` | Admin | Deactivate product |
| POST | `/api/shop/checkout` | Public | Create Stripe Checkout Session |
| POST | `/api/shop/webhook` | Stripe | Payment confirmation webhook |

---

## YourArea Community API

The fan community behind `/yourarea`. Auth is **passwordless magic-link**: signup/login email a
one-time link (15 min TTL, single-use, stored hashed); exchanging it at `/verify` returns a JWT
used as `Authorization: Bearer <token>` everywhere else. In local dev the "email" is written to
the application log by `LoggingEmailSender` (an SES implementation is the production seam — see
`community/service/EmailSender.java`).

Errors use stable machine-readable codes:
`{"code":"USERNAME_TAKEN","error":"That username is already taken"}`. Codes:
`USERNAME_INVALID`, `USERNAME_TAKEN`, `EMAIL_TAKEN`, `NO_ACCOUNT`, `LINK_INVALID`, `LINK_EXPIRED`,
`NOT_A_MEMBER`, `NOT_FOUND`, `NOT_OFFICIAL`, `INVALID_ACCENT`, `TOP8_LIMIT`, `INVALID_RECIPIENT`,
`THREAD_EXISTS`, `THREAD_REVOKED`, `THREAD_CLOSED`, `NOT_REVOKER`, `UNAUTHENTICATED`.

A configurable word filter (`community.word-filter.blocklist`, comma-separated) stars out matched
words (`damn` → `d***`) **at write time** in forum threads/replies, wall comments, and DM bodies;
the filtered text is what gets stored.

### Auth (magic link)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/community/auth/signup` | Public | Request signup link. Body `{"email","username"}` (username 3–24 chars, `[a-z0-9_]`). → `{"ok":true,"message"}` or `USERNAME_INVALID` (400) / `USERNAME_TAKEN` (409) / `EMAIL_TAKEN` (409). The username is claimed only after verification. |
| POST | `/api/community/auth/login` | Public | Request login link. Body `{"email"}`. → `{"ok":true,...}` or `NO_ACCOUNT` (404). |
| POST | `/api/community/auth/verify` | Public | Exchange the emailed one-time token. Body `{"token"}`. → `{"token":"<jwt>","username","displayName","official"}` or `LINK_INVALID` / `LINK_EXPIRED` (400). |
| GET | `/api/community/auth/me` | JWT | Own account: `{"email","profile":{...ProfileDto}}`. |

Example:
```
POST /api/community/auth/signup  {"email":"fan@example.com","username":"fan_one"}
→ 200 {"ok":true,"message":"Check your email for a verification link"}
POST /api/community/auth/verify  {"token":"<from the emailed link>"}
→ 200 {"token":"eyJ...","username":"fan_one","displayName":"fan_one","official":false}
```

### Profiles
`ProfileDto`: `{username, displayName, avatarUrl, about, whoToMeet, mood, profileSong, official,
themeAccent (EMBER|VIOLET|MOSS|GOLD|ICE), themeGlitter, themeTiledBg, memberSince,
topFriends:[{username,displayName,avatarUrl,official}]}`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/community/profiles/{username}` | Public | Profile incl. Top 8. |
| PUT | `/api/community/profiles/me` | JWT | Full-replace own profile. Body = ProfileDto fields minus `username`/`official`/`memberSince`; `topFriends` = list of ≤8 usernames. Errors: `INVALID_ACCENT`, `TOP8_LIMIT`, `NOT_FOUND` (unknown friend). The `official` flag can never be set via the API. |
| GET | `/api/community/profiles/{username}/wall` | Public | Wall comments, newest first. |
| POST | `/api/community/profiles/{username}/wall` | JWT | Body `{"body","glitter":bool}` → WallCommentDto (body word-filtered). |

### Bulletins (official account only)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/community/bulletins?page&size` | Public | Newest first (paged, `content[]`). |
| POST | `/api/community/bulletins` | JWT (official) | Body `{"title","body"}`. Non-official → 403 `NOT_OFFICIAL`. |

### Forum
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/community/boards` | Public | Boards with thread counts (seeded: `general`, `releases`, `live`). |
| GET | `/api/community/boards/{slug}/threads?page&size` | Public | Thread summaries, newest first, with reply counts. |
| POST | `/api/community/boards/{slug}/threads` | JWT | Body `{"title","body"}` → ThreadDetailDto (word-filtered). |
| GET | `/api/community/threads/{id}` | Public | Thread + replies (oldest first). |
| POST | `/api/community/threads/{id}/replies` | JWT | Body `{"body"}` → ReplyDto (word-filtered). |

### DMs (request-gated — all endpoints JWT)
First contact creates a **message request**. Accept opens a two-way thread whose first message is
the request body. Decline is silent — the sender still sees it in Sent and may request again.
"Revoke access" on a thread = block: closes it both ends, hides it from the blocked side, and
silently swallows the blocked side's new requests (they still get `{"ok":true}`); only the
revoker can reopen (re-accept).

| Method | Path | Description |
|---|---|---|
| POST | `/api/community/dms/requests` | Body `{"toUsername","message"}` → `{"ok":true,"message":"Message sent"}` (also when silently blocked). Real errors: `INVALID_RECIPIENT` (400), `THREAD_EXISTS` (409, open thread already), `THREAD_REVOKED` (409, you revoked — reopen instead), `NOT_FOUND` (404). |
| GET | `/api/community/dms/requests` | Incoming pending requests `[{id, from, preview, createdAt}]`. |
| POST | `/api/community/dms/requests/{id}/accept` | → `{"ok":true,"threadId":n}`. |
| POST | `/api/community/dms/requests/{id}/decline` | → `{"ok":true}` (sender never notified). |
| GET | `/api/community/dms/inbox` | Open threads (revoked ones shown only to the revoker): `[{id, with, lastMessagePreview, lastActivityAt, unreadCount, status}]`, newest activity first. |
| GET | `/api/community/dms/sent` | `{"requests":[...outgoing pending+declined, indistinguishable...],"messages":[...]}`. |
| GET | `/api/community/dms/trash` | Threads the caller trashed. |
| GET | `/api/community/dms/threads/{id}` | Messages (oldest first); marks the thread read for the caller. |
| POST | `/api/community/dms/threads/{id}/messages` | Body `{"body"}` → DmMessageDto (word-filtered). Closed thread → 403 `THREAD_CLOSED`. |
| POST | `/api/community/dms/threads/{id}/revoke` | Block: close the thread both ends. |
| POST | `/api/community/dms/threads/{id}/reopen` | Re-accept; revoker only (403 `NOT_REVOKER`). |
| POST | `/api/community/dms/threads/{id}/trash` | Soft-delete the thread for the caller only. |
| POST | `/api/community/dms/threads/{id}/restore` | Undo trash. |

### Community environment variables
| Variable | Required | Description |
|---|---|---|
| `MAGIC_LINK_BASE_URL` | Prod | Frontend base URL the emailed magic links point at (default `http://localhost:5173`). |
| `WORD_FILTER_BLOCKLIST` | No | Comma-separated blocklist override. |
| `OFFICIAL_EMAIL` | First run | Email for the seeded official villxin account (logs in via magic link; no password). |
| `OFFICIAL_USERNAME` | No | Username for the official account (default `villxin`). |

### Mailing list response shape (updated)
`POST /api/subscribe` `{"email"}` → `{"ok":true,"message":"Subscribed!"}`;
duplicate → 409 `{"ok":false,"code":"duplicate","message":"Already subscribed"}`;
invalid → 400 `{"ok":false,"code":"invalid","message":"Invalid email address"}`.
