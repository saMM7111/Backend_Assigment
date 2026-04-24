# Phase 1: Core API & Database Setup

A Spring Boot 3.x backend service with PostgreSQL and Redis, implementing the foundational entities and REST endpoints.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Data Model](#data-model)
- [API Reference](#api-reference)
- [Local Setup](#local-setup)
- [Seed Data](#seed-data)
- [Postman Collection](#postman-collection)
- [Phase 2 Approach (Thread Safety)](#phase-2-approach-thread-safety)
- [Phase 3 Approach (Notification Engine)](#phase-3-approach-notification-engine)
- [Phase 4 Approach (Corner Cases)](#phase-4-approach-corner-cases)

---

## Overview

Phase 1 establishes the core infrastructure:

- Spring Boot service wired to PostgreSQL via JPA/Hibernate
- Redis connection scaffolded and ready for Phase 2 guardrails
- Three REST endpoints for creating posts, adding comments, and liking posts
- Input validation with clean, structured error responses
- Auto-seeded actors (users + bot) for immediate testability
- Smart Phase 3 notification batching to avoid bot-notification spam
- Phase 4 hardening for race conditions, statelessness, and transaction integrity

---

## Tech Stack

| Layer        | Technology                        |
|--------------|-----------------------------------|
| Language     | Java 17                           |
| Framework    | Spring Boot 3.x                   |
| Database     | PostgreSQL (via JPA / Hibernate)  |
| Cache        | Redis (scaffolded for Phase 2)    |
| Build Tool   | Maven                             |
| Dev Infra    | Docker Compose                    |

---

## Project Structure

```
project/
├── docker-compose.yml
├── pom.xml
├── postman/
│   └── Phase1.postman_collection.json
└── src/
    └── main/
        ├── java/com/app/
        │   ├── controller/        # REST controllers
        │   ├── dto/               # Request / response DTOs
        │   ├── entity/            # JPA entities
        │   ├── repository/        # Spring Data JPA repositories
        │   ├── service/           # Business logic
        │   └── Application.java
        └── resources/
            └── application.yml
```

---

## Data Model

### User
| Field        | Type    | Notes                    |
|--------------|---------|--------------------------|
| id           | Long    | Primary key              |
| username     | String  | Unique                   |
| is_premium   | Boolean | Premium status flag      |

### Bot
| Field               | Type   | Notes                    |
|---------------------|--------|--------------------------|
| id                  | Long   | Primary key              |
| name                | String | Bot identifier           |
| persona_description | String | Bot persona/description  |

### Post
| Field       | Type      | Notes                          |
|-------------|-----------|--------------------------------|
| id          | Long      | Primary key                    |
| author_id   | Long      | FK → User or Bot               |
| content     | String    | Post body                      |
| like_count  | Integer   | Tracks total likes             |
| created_at  | Timestamp | Auto-set on creation           |

### Comment
| Field       | Type      | Notes                          |
|-------------|-----------|--------------------------------|
| id          | Long      | Primary key                    |
| post_id     | Long      | FK → Post                      |
| author_id   | Long      | FK → User or Bot               |
| content     | String    | Comment body                   |
| depth_level | Integer   | Nesting depth (1 = top-level)  |
| created_at  | Timestamp | Auto-set on creation           |

---

## API Reference

Base URL: `http://localhost:8080`

---

### `POST /api/posts` — Create a Post

**Request body**
```json
{
  "authorType": "USER",
  "authorId": 1,
  "content": "Launching the first draft of my post"
}
```

| Field      | Type   | Required | Values         |
|------------|--------|----------|----------------|
| authorType | String | ✅       | `USER`, `BOT`  |
| authorId   | Long   | ✅       | Existing ID    |
| content    | String | ✅       | Non-empty      |

---

### `POST /api/posts/{postId}/comments` — Add a Comment

**Request body**
```json
{
  "authorType": "BOT",
  "authorId": 3,
  "content": "Nice post. I can help summarize the feedback.",
  "depthLevel": 1
}
```

| Field      | Type    | Required | Values         |
|------------|---------|----------|----------------|
| authorType | String  | ✅       | `USER`, `BOT`  |
| authorId   | Long    | ✅       | Existing ID    |
| content    | String  | ✅       | Non-empty      |
| depthLevel | Integer | ✅       | ≥ 1            |

---

### `POST /api/posts/{postId}/like` — Like a Post

**Request body**
```json
{
  "actorType": "USER",
  "actorId": 2
}
```

| Field     | Type   | Required | Values         |
|-----------|--------|----------|----------------|
| actorType | String | ✅       | `USER`, `BOT`  |
| actorId   | Long   | ✅       | Existing ID    |

---

## Local Setup

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven 3.8+

---

### Step 1 — Start PostgreSQL and Redis

```bash
docker compose up -d
```

This starts:

| Service    | Host & Port       | Credentials                              |
|------------|-------------------|------------------------------------------|
| PostgreSQL | localhost:5433    | db: `phase1`, user/pass: `postgres`      |
| Redis      | localhost:6379    | —                                        |

---

### Step 2 — Run the Application

```bash
mvn spring-boot:run
```

The app starts on **`http://localhost:8080`**.

On first boot, database tables are auto-created by Hibernate and seed data is inserted automatically.

---

## Seed Data

The following actors are seeded on first run to make Phase 1 endpoints immediately testable:

| Type | ID | Identifier          | Notes             |
|------|----|---------------------|-------------------|
| User | 1  | `alice`             | `is_premium=false` |
| User | 2  | `bob`               | `is_premium=true`  |
| Bot  | 3  | `zen-bot`           | —                  |

> **Note:** IDs may differ if your database already contains data.

---

## Postman Collection

A ready-to-import Postman collection covering all Phase 1 endpoints is included at:

```
postman/Phase1.postman_collection.json
```

Import it via **Postman → File → Import** and point the base URL to `http://localhost:8080`.

---

## Phase 2 Approach (Thread Safety)

For Phase 2, I treated Redis as the gatekeeper and PostgreSQL as the source of truth.

In simple terms, every risky bot interaction is checked in Redis first (atomic lock rules), and only then persisted in PostgreSQL. This keeps the API stateless and safe under concurrent requests.

### How I guaranteed thread safety for Atomic Locks

1. Horizontal cap (max 100 bot replies per post)

- I use Redis atomic `INCR` on `post:{postId}:bot_count`.
- Every request gets a unique increment result, even under heavy concurrency.
- If the incremented value is greater than 100, I reject the request with `429 Too Many Requests` and immediately `DECR` the counter.
- Because `INCR/DECR` are atomic in Redis, parallel requests cannot bypass this limit.

2. Cooldown cap (same bot cannot target same human twice in 10 minutes)

- I use Redis `SET` with `NX` + TTL 10 minutes (`setIfAbsent(..., Duration.ofMinutes(10))`) on key `cooldown:bot_{id}:human_{id}`.
- This operation is atomic: only the first concurrent request can create the key.
- All other concurrent requests for the same bot-human pair fail instantly and return `429`.

3. Vertical cap (max thread depth 20)

- I validate `depthLevel` before writing anything.
- If `depthLevel > 20`, the request is rejected with `429`.

### Keeping Redis and DB consistent

- Bot guardrails are reserved before writing the comment.
- If comment save or scoring fails, reservation is rolled back (`bot_count` decremented and cooldown key removed if needed).
- This rollback prevents stale lock state and keeps Redis counters accurate.

### Virality score (real-time)

- I update `post:{postId}:virality_score` in Redis with atomic increments:
  - Bot reply: `+1`
  - Human like: `+20`
  - Human comment: `+50`

This gives fast real-time scoring while preserving correctness under parallel traffic.

---

## Phase 3 Approach (Notification Engine)

Phase 3 introduces a Redis-backed notification strategy that reduces spam while still keeping users informed.

### 1. Redis throttler (15-minute cooldown)

When a bot interacts with a user's post, the system checks a cooldown key:

- Cooldown key: `user:{id}:notif_cooldown`
- If cooldown exists: append a message to `user:{id}:pending_notifs`
- If cooldown does not exist: log `Push Notification Sent to User` and set cooldown TTL to 15 minutes

This means users do not get flooded with repeated bot push notifications in a short time window.

### 2. Scheduled sweeper (every 5 minutes)

A Spring `@Scheduled` job runs every 5 minutes and scans pending notification lists (`user:*:pending_notifs`).

For each user list, it:

- Pops all queued messages from Redis
- Counts how many interactions were buffered
- Logs a summarized message in the format:
  - `Summarized Push Notification: Bot X and [N] others interacted with your posts.`

This keeps the user experience calm and digestible while preserving interaction visibility.

---

## Phase 4 Approach (Corner Cases)

Phase 4 focuses on proving backend correctness under stress and failure conditions.

### 1. Race condition safety (200 concurrent bot comments)

- The horizontal cap remains Redis-atomic (`INCR` / `DECR`) and blocks requests once the counter exceeds 100.
- To avoid drift after restarts, the Redis bot-counter key is seeded from PostgreSQL bot-comment count when missing.
- Result: even under burst concurrency, accepted bot comments for one post are capped at 100.

### 2. Statelessness guarantee

- No in-memory state is used for counters, cooldowns, or notification queues.
- All guardrails and notification buffers are stored in Redis keys/lists.
- The application instances remain stateless and horizontally scalable.

### 3. Data integrity between Redis and PostgreSQL

- Redis guardrails are reserved before write attempts.
- Reservation rollback is now bound to transaction completion status, so failed transactions release Redis reservations safely.
- Redis side-effects (virality increments and notifications) run after successful DB commit to prevent phantom updates when commits fail.

### 4. Integration test coverage for Phase 4

Integration tests were added in `src/test/java/com/sam/assigment/service/Phase4CornerCasesIT.java` to validate:

- 200 concurrent bot comment attempts produce exactly 100 persisted bot comments
- Redis reservation rollback on failed bot comment validation

Run the Phase 4 integration suite explicitly with:

```bash
mvn -Dphase4.it=true -Dtest=Phase4CornerCasesIT test
```

The tests are opt-in so default local/CI runs are not blocked in environments without Docker.
