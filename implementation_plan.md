# 🚀 LinkBox — All-in-One Spring Boot Platform

> **Shrib + Send Anywhere + URL Shortener** in a single, production-ready Spring Boot application.

---

## Overview

**LinkBox** is a unified web utility platform that gives users three powerful tools from a single beautiful interface:

| Module | Inspired By | What It Does |
|---|---|---|
| 📝 **Scribble** | Shrib.com | Create, save, and share plain text / code snippets instantly — no account needed |
| 📦 **Dropzone** | SendAnywhere | Upload a file, get a 6-digit code — anyone with the code can download it |
| 🔗 **Shrink** | Bit.ly / TinyURL | Paste a long URL, get a short one with click tracking |

---

## Tech Stack

### Backend
| Layer | Technology | Reason |
|---|---|---|
| Framework | **Spring Boot 3.3** (Java 21) | LTS, virtual threads, production-ready |
| Build Tool | **Maven** | Simpler for Spring projects |
| ORM | **Spring Data JPA + Hibernate** | Clean entity management |
| Database | **PostgreSQL 16** | Best for production: indexing, JSONB, reliability |
| File Storage | **Local Filesystem** (phase 1) → **MinIO/S3** (phase 2) | Start simple, cloud-ready |
| Caching | **Redis** (optional, for rate-limiting & URL cache) | Fast lookups |
| Security | **Spring Security + JWT** | Optional auth for premium features |
| Validation | **Jakarta Bean Validation** | Input safety |
| Scheduling | **Spring Scheduler** | Auto-expire old pastes / files / links |
| Docs | **SpringDoc OpenAPI (Swagger)** | Auto-generated REST docs |

### Frontend
| Layer | Technology |
|---|---|
| Structure | HTML5 (Thymeleaf templates served by Spring Boot) |
| Styling | Vanilla CSS (custom design system, dark mode) |
| Logic | Vanilla JavaScript (ES6+, fetch API) |
| Icons | Lucide Icons (CDN) |
| Fonts | Google Fonts — Inter |
| QR Codes | qrcodejs library (CDN) |

### Deployment
- **Packaging**: Executable JAR (`java -jar`)
- **Containerization**: `Dockerfile` + `docker-compose.yml` included
- **Environment**: `application-prod.properties` with env-variable overrides
- **Reverse Proxy**: Nginx config included

---

## Database Schema (PostgreSQL)

```sql
-- URL Shortener
CREATE TABLE short_urls (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code    VARCHAR(10) UNIQUE NOT NULL,
    original_url  TEXT NOT NULL,
    title         VARCHAR(255),
    created_at    TIMESTAMP DEFAULT NOW(),
    expires_at    TIMESTAMP,
    click_count   BIGINT DEFAULT 0,
    password_hash VARCHAR(255),   -- nullable, for protected links
    is_active     BOOLEAN DEFAULT TRUE
);

-- Text / Code Pastes (Scribble)
CREATE TABLE pastes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          VARCHAR(20) UNIQUE NOT NULL,
    content       TEXT NOT NULL,
    language      VARCHAR(50) DEFAULT 'plaintext',
    title         VARCHAR(255),
    created_at    TIMESTAMP DEFAULT NOW(),
    expires_at    TIMESTAMP,
    password_hash VARCHAR(255),
    view_count    BIGINT DEFAULT 0,
    is_active     BOOLEAN DEFAULT TRUE
);

-- File Transfers (Dropzone)
CREATE TABLE file_transfers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_code VARCHAR(6) UNIQUE NOT NULL,  -- e.g. "482915"
    file_name     VARCHAR(500) NOT NULL,
    file_size     BIGINT NOT NULL,
    mime_type     VARCHAR(255),
    storage_path  TEXT NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW(),
    expires_at    TIMESTAMP NOT NULL,
    download_count INT DEFAULT 0,
    max_downloads INT DEFAULT 1,
    password_hash VARCHAR(255),
    is_active     BOOLEAN DEFAULT TRUE
);

-- Analytics (optional)
CREATE TABLE url_clicks (
    id          BIGSERIAL PRIMARY KEY,
    short_url_id UUID REFERENCES short_urls(id),
    clicked_at  TIMESTAMP DEFAULT NOW(),
    referrer    TEXT,
    user_agent  TEXT,
    country     VARCHAR(10)
);
```

---

## Project Structure

```
linkbox/
├── src/
│   ├── main/
│   │   ├── java/com/linkbox/
│   │   │   ├── LinkboxApplication.java
│   │   │   ├── config/
│   │   │   │   ├── WebConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── SchedulerConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── HomeController.java          ← serves index.html
│   │   │   │   ├── PasteController.java         ← Scribble API
│   │   │   │   ├── FileTransferController.java  ← Dropzone API
│   │   │   │   ├── UrlShortenerController.java  ← Shrink API
│   │   │   │   └── RedirectController.java      ← /{shortCode} redirect
│   │   │   ├── model/
│   │   │   │   ├── Paste.java
│   │   │   │   ├── FileTransfer.java
│   │   │   │   └── ShortUrl.java
│   │   │   ├── repository/
│   │   │   │   ├── PasteRepository.java
│   │   │   │   ├── FileTransferRepository.java
│   │   │   │   └── ShortUrlRepository.java
│   │   │   ├── service/
│   │   │   │   ├── PasteService.java
│   │   │   │   ├── FileTransferService.java
│   │   │   │   ├── UrlShortenerService.java
│   │   │   │   └── CleanupService.java          ← scheduled expiry cleanup
│   │   │   ├── dto/
│   │   │   │   ├── PasteRequest.java / PasteResponse.java
│   │   │   │   ├── FileTransferResponse.java
│   │   │   │   └── UrlShortenRequest.java / UrlShortenResponse.java
│   │   │   └── util/
│   │   │       ├── CodeGenerator.java           ← 6-digit codes, short slugs
│   │   │       └── PasswordHashUtil.java
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   │   └── style.css
│   │       │   └── js/
│   │       │       ├── app.js           ← tab switching, global state
│   │       │       ├── scribble.js      ← paste module
│   │       │       ├── dropzone.js      ← file upload module
│   │       │       └── shrink.js        ← URL shortener module
│   │       ├── templates/
│   │       │   └── index.html           ← single SPA shell
│   │       ├── application.properties
│   │       └── application-prod.properties
├── docker/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── nginx.conf
└── pom.xml
```

---

## REST API Endpoints

### 📝 Scribble (Pastes)
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/paste` | Create a new paste |
| `GET` | `/api/paste/{slug}` | Retrieve paste by slug |
| `POST` | `/api/paste/{slug}/verify` | Verify password for protected paste |
| `DELETE` | `/api/paste/{slug}` | Delete paste (if no password, anyone; else password required) |

### 📦 Dropzone (File Transfer)
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/transfer` | Upload file → get 6-digit code |
| `GET` | `/api/transfer/{code}` | Get file metadata by code |
| `GET` | `/api/transfer/{code}/download` | Download the file |
| `POST` | `/api/transfer/{code}/verify` | Verify password |

### 🔗 Shrink (URL Shortener)
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/url/shorten` | Shorten a URL |
| `GET` | `/api/url/{code}/info` | Get link metadata + stats |
| `GET` | `/{code}` | **Redirect** to original URL (via RedirectController) |
| `DELETE` | `/api/url/{code}` | Delete short URL |

---

## Frontend Pages (Single Page App in `index.html`)

The app has **one HTML page** with **three tab panels** and a sleek top navbar.

### Design System
- **Dark mode** primary (with light toggle)
- Color palette: Deep navy `#0a0f1e` bg, electric blue `#3b82f6` accent, emerald `#10b981` success
- Glassmorphism cards with blur + subtle border
- Smooth tab transition animations
- Fully responsive (mobile-first)

### Tab 1 — Scribble (Shrib-like)
- Large monospace textarea (full-height editor feel)
- Language selector dropdown (for syntax hint display)
- Optional title input
- **Expiry** selector: 1h / 24h / 7d / 30d / Never
- **Password protection** toggle
- `[Share]` → copies share link + shows QR code
- View page: read-only with copy button, shows view count

### Tab 2 — Dropzone (Send Anywhere-like)
- **Drag & drop zone** OR file picker button
- Shows file name, size, type preview
- **Expiry**: 10min / 1h / 24h
- **Max downloads**: 1 / 3 / 10 / Unlimited
- Optional password
- On upload → animated progress bar → shows **large 6-digit code** + QR
- **Receive tab**: Enter 6-digit code → see file details → download button

### Tab 3 — Shrink (URL Shortener)
- URL input with paste button
- Optional custom alias
- Expiry selector
- Optional password protection
- Result card: short URL + copy button + QR code + click stats
- Mini analytics: clicks today / total / last-click time

---

## Suggested Bonus Features

| Feature | Details |
|---|---|
| 🔐 **Password Protection** | BCrypt-hashed passwords on any resource |
| ⏰ **Auto-Expiry + Cleanup** | Spring `@Scheduled` task runs every hour to delete expired rows + files |
| 📊 **Click Analytics** | Track clicks per short URL (referrer, time) |
| 📱 **QR Code Generation** | Client-side QR via qrcodejs for every share |
| 🎨 **Syntax Highlighting** | Paste viewer uses Prism.js to highlight code |
| 🌙 **Dark/Light Mode** | CSS variable toggle, saved to localStorage |
| 📋 **Copy to Clipboard** | One-click copy on all share links |
| 🔒 **Rate Limiting** | IP-based limits (Bucket4j library) to prevent abuse |
| 🗂️ **File Type Icons** | Visual file type indicators on Dropzone |
| 🌐 **OpenAPI Docs** | Auto-generated Swagger UI at `/swagger-ui.html` |
| 🐳 **Docker Support** | `docker-compose up` starts app + PostgreSQL |
| 🔁 **Custom Aliases** | Users can request a custom short-code for URLs |

---

## Key Libraries (pom.xml dependencies)

```xml
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-thymeleaf
spring-boot-starter-validation
spring-boot-starter-security
postgresql (runtime)
lombok
springdoc-openapi-starter-webmvc-ui   <!-- Swagger -->
bucket4j-core                          <!-- Rate limiting -->
```

---

## Deployment Strategy

### Option A — JAR (Simple VPS)
```bash
./mvnw clean package -DskipTests
java -jar target/linkbox.jar --spring.profiles.active=prod
```

### Option B — Docker Compose (Recommended)
```bash
docker-compose up -d   # Starts app + PostgreSQL automatically
```

### Nginx Reverse Proxy
```nginx
server {
    listen 80;
    server_name yourdomain.com;
    client_max_body_size 100M;   # for file uploads

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
    }
}
```

---

## Open Questions

> [!IMPORTANT]
> **File size limit** — What is the maximum file size you want to support for Dropzone uploads? (Suggested: 100MB)

> [!IMPORTANT]
> **Domain** — Will you use a custom domain? This matters for how short URLs are generated (e.g., `lnk.bx/abc123`).

> [!IMPORTANT]
> **User Accounts** — Do you want user registration/login so users can manage their pastes/links, or should everything be anonymous (no accounts)?

> [!IMPORTANT]
> **File Storage** — Should uploaded files be stored on the local server disk, or do you have cloud storage (AWS S3, Cloudflare R2) available?

> [!NOTE]
> If no user accounts are needed, Spring Security will be used only to protect the admin/cleanup APIs and rate-limit abuse — not for user login flows.
