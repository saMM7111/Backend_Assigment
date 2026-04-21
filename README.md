# Assigment — Phase 1: Core API & Database Setup

A Spring Boot 3.x backend service with PostgreSQL and Redis, implementing the foundational entities and REST endpoints for the Grid07 platform.

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
- [Upcoming: Phase 2](#upcoming-phase-2)

---

## Overview

Phase 1 establishes the core infrastructure of Grid07:

- Spring Boot service wired to PostgreSQL via JPA/Hibernate
- Redis connection scaffolded and ready for Phase 2 guardrails
- Three REST endpoints for creating posts, adding comments, and liking posts
- Input validation with clean, structured error responses
- Auto-seeded actors (users + bot) for immediate testability

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
grid07/
├── docker-compose.yml
├── pom.xml
├── postman/
│   └── Grid07-Phase1.postman_collection.json
└── src/
    └── main/
        ├── java/com/grid07/
        │   ├── controller/        # REST controllers
        │   ├── dto/               # Request / response DTOs
        │   ├── entity/            # JPA entities
        │   ├── repository/        # Spring Data JPA repositories
        │   ├── service/           # Business logic
        │   └── Grid07Application.java
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
| PostgreSQL | localhost:5433    | db: `grid07`, user/pass: `postgres`      |
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
postman/Grid07-Phase1.postman_collection.json
```

Import it via **Postman → File → Import** and point the base URL to `http://localhost:8080`.

---

## Upcoming: Phase 2

Phase 2 will layer guardrails and rate-limiting on top of this foundation, making use of the Redis connection already established in Phase 1.
