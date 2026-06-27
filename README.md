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
