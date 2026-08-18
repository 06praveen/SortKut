# 🚀 SortKut — High-Performance All-in-One Utility Platform

[![Built with Java](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4--RC1-brightgreen.svg?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/PostgreSQL-Neon_Serverless-blue.svg?style=for-the-badge&logo=postgresql)](https://neon.tech/)
[![Deployment](https://img.shields.io/badge/Docker-Render.com-blue.svg?style=for-the-badge&logo=docker)](https://render.com/)

> **Paste, Send, and Shrink instantly.** A premium, modern, and high-performance anonymous web utility suite designed to share text/code, transfer massive files via short-codes, and shrink long URLs with zero registration or logins required.

---

## 📖 Table of Contents

1. [🌟 Platform Core Modules](#-platform-core-modules)
2. [📐 System Architecture & Topology](#-system-architecture--topology)
3. [📝 Technical Deep-Dive: Scribble (Pastebin Engine)](#-technical-deep-dive-scribble-pastebin-engine)
4. [📦 Technical Deep-Dive: Dropzone (1GB File Transfer)](#-technical-deep-dive-dropzone-1gb-file-transfer)
5. [🔗 Technical Deep-Dive: Shrink (URL Shortener & Analytics)](#-technical-deep-dive-shrink-url-shortener--analytics)
6. [🚀 Production Deployment Guide (100% Free Cloud Tier)](#-production-deployment-guide-100-free-cloud-tier)
7. [📈 Traffic & Storage Guardrails](#-traffic--storage-guardrails)
8. [🛠️ Local Development & Setup](#️-local-development--setup)

---

## 🌟 Platform Core Modules

SortKut consolidates three critical utilities into a single, unified, modern web interface.

| Module | What it Accomplishes | Engineering & UX Highlights |
| :--- | :--- | :--- |
| **📝 Scribble** | Sleek, anonymous, and ephemeral text and code snippet sharing engine (similar to Pastebin or Shrib). | Prism.js syntax highlighting, $O(1)$ database B-Tree slug index, secure session-locked password gateway, random URL entropy. |
| **📦 Dropzone** | Send Anywhere-like secure file sharing allowing uploads up to **1GB** using local folder storage. | Safe background disk cleaner (wipes expired data & bypasses Windows file-locks), dynamic MIME types, auto-routing QR Code lookup. |
| **🔗 Shrink** | Advanced URL shortener (similar to Bit.ly) with real-time tracking metrics and custom aliases. | Click counter API (Total/Today/Last Clicked), address bar spoofing safety, reserved keywords proxying, `localStorage` active state preservation. |

---

## 📐 System Architecture & Topology

SortKut is built on a clean, decoupled **Spring Boot MVC & REST API Architecture**, interacting with a PostgreSQL database and streaming files directly from physical storage blocks on the server container.

```mermaid
graph TD
    %% Clients
    User([Browser Client]) -->|1. AJAX Requests & Navigation| Frontend[Thymeleaf & Custom Vanilla CSS / JS UI]
    QR[Mobile QR Scanner] -->|Direct URL Scan /?code=XYZ| Frontend
    
    %% Controllers
    Frontend -->|POST /api/paste| RestCtrl1[PasteRestController]
    Frontend -->|POST /api/transfer| RestCtrl2[FileTransferRestController]
    Frontend -->|POST /api/url/shorten| RestCtrl3[UrlRestController]
    Frontend -->|GET /p/{slug} & GET /{code}| HomeCtrl[HomeController]
    
    %% Services
    RestCtrl1 -->|Persist snippet| PasteService[PasteService]
    RestCtrl2 -->|Stream file & generate code| FileService[FileTransferService]
    RestCtrl3 -->|Shorten URL & count clicks| UrlService[ShortUrlService]
    HomeCtrl -->|Resolve session auth & route links| PasteService
    HomeCtrl -->|Intercept redirects| UrlService
    
    %% Storage & Database
    PasteService -->|B-Tree Index Lookup| DB[(Neon Serverless PostgreSQL)]
    FileService -->|Metadata & Counts| DB
    UrlService -->|Redirections & Analytics| DB
    
    FileService -->|Low-Memory Chunked Streams| Storage[(Physical Uploads Folder /uploads)]
    
    %% Schedulers
    Cleaner[Scheduled Background Sweeper] -->|Cron: Every 5 Mins| PasteService
    Cleaner -->|Cron: Sweep Expired Physical Files| FileService
    Cleaner -->|Cron: Purge Expired URLs| UrlService
```

---

## 📝 Technical Deep-Dive: Scribble (Pastebin Engine)

Scribble supports arbitrarily large code uploads, formatting variables client-side using Prism.js, while retaining a lightweight persistence architecture.

### 1. Database Index Mapping (`Paste.java`)
We mapped the JPA Entity to PostgreSQL with a **unique B-Tree index** on the `slug` column. This configuration prevents full-table sequential scans ($O(N)$) and ensures database reads resolve in **$O(1)$ constant time**.

```java
@Entity
@Table(name = "pastes", indexes = {
    @Index(name = "idx_paste_slug", columnList = "slug", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Paste {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // TEXT definition for rich text/heavy payloads

    @Column(nullable = false, length = 50)
    private String language;

    @Column(length = 255)
    private String password; // Plain-text gates for session validation

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
```

### 2. Collision-Resistant Generation (`PasteService.java`)
Using a cryptographically secure random number generator (`SecureRandom`), the system constructs highly unpredictable 7-character slugs selected from an alphanumeric alphabet. The number of unique keys exceeds **$62^7 \approx 3.5$ trillion combinations**, preventing brute-force scraping attacks. A `do-while` loop guarantees collision safety prior to database commits.

```java
public Paste createPaste(PasteRequest request) {
    String slug;
    do {
        slug = generateSlug(7);
    } while (pasteRepository.findBySlug(slug).isPresent());
    
    // ... calculate expiration and save ...
}
```

### 3. Session-Locked Authorization Gateways
When a paste is password-protected, the server blocks rendering and presents a security gateway card. Upon typing the correct password, the client receives an authorization stamp stored inside the server's `HttpSession`. 

Subsequent requests retrieve the session cookie automatically, allowing the user to refresh the page without entering the password repeatedly during their browser session:
```java
session.setAttribute("unlocked_paste_" + slug, true);
```

---

## 📦 Technical Deep-Dive: Dropzone (1GB File Transfer)

Dropzone functions as an anonymous, secure file transfer system. Users upload files up to **1GB**, obtaining a numeric **6-digit share code** and a matching QR code. 

### 1. Spring Multipart Allocation & Tomcat Limits
By default, standard servlet containers reject file uploads larger than 10MB. To allow up to **1GB** files to stream seamlessly, we configured Spring Boot's multipart variables using whole-number sizes to avoid Tomcat runtime crashes:
```properties
spring.servlet.multipart.max-file-size=1GB
spring.servlet.multipart.max-request-size=1100MB
```

### 2. Windows File-Locking & Ephemeral Storage Purging
> [!IMPORTANT]
> **The System Design Challenge:** If we delete files immediately inside the active HTTP download thread once the download limit is reached, we trigger a critical race condition. 
> 
> On Windows-based systems, the operating system locks active files that are open for reading. Streaming a file and deleting it simultaneously throws a file-lock exception and corrupts the download.
> 
> **The Engineered Solution:** 
> 1. When the maximum download threshold is reached (e.g., `1` download), the application immediately updates the database.
> 2. Subsequent HTTP requests find the file blocked and return `404 Not Found`, denying entry.
> 3. A background `@Scheduled` cron job running every 5 minutes queries the database for all expired or limit-reached items, safely sweeps the locked physical files from the disk, and then purges the DB metadata:

```java
@Scheduled(cron = "0 */5 * * * *")
@Transactional
public void purgeExpiredTransfers() {
    List<FileTransfer> toPurge = fileTransferRepository.findPurgeableTransfers(LocalDateTime.now());
    for (FileTransfer transfer : toPurge) {
        try {
            Path filePath = Paths.get(transfer.getStoragePath());
            Files.deleteIfExists(filePath); // Safe physical removal from storage disk
            fileTransferRepository.delete(transfer);
        } catch (IOException e) {
            log.error("Failed to delete physical file: " + transfer.getStoragePath(), e);
        }
    }
}
```

### 3. Dynamic MIME Type Resolution
Serving all downloads under a general `application/octet-stream` header forces the browser to guess the format, frequently causing images, PDFs, or code files to download as raw txt. 
To resolve this, Dropzone records the file's original MIME content-type on upload, storing it in the database and injecting it dynamically into the response header:
```java
return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + transfer.getFileName() + "\"")
        .contentType(MediaType.parseMediaType(transfer.getMimeType())) // Exact type representation
        .body(resource);
```

### 4. Direct QR-Code Mobile Auto-Routing
On completion, client-side scripts build a QR Code with the URL format `https://domain.com/?code=123456`. When a mobile user scans the QR code:
1. The frontend initializer detects the presence of the `?code` parameter in the URL.
2. It automatically switches the active tab view to the "Dropzone" section, activates the "Receive File" frame, enters the 6-digit code, and triggers the download details fetcher automatically.
3. It silently sweeps the URL query parameter using HTML5 History API:
   ```javascript
   window.history.replaceState({}, document.title, window.location.pathname);
   ```
   This ensures page reloads do not keep re-triggering lookups.

---

## 🔗 Technical Deep-Dive: Shrink (URL Shortener & Analytics)

Shrink redirects URLs dynamically through root path interception (`GET /{code}`) and feeds detailed live click analytics to the frontend.

```mermaid
sequenceDiagram
    autonumber
    Browser Client->>HomeController: GET /my-alias
    HomeController->>ShortUrlService: Look up Short Code "my-alias"
    ShortUrlService->>DB: Query Index idx_short_code
    DB-->>ShortUrlService: Return URL metadata & Password hash
    Note over ShortUrlService: Validate Expiry & Active States
    alt is Password Protected & Session Unauthorized
        ShortUrlService-->>HomeController: Flag Unauthorized
        HomeController-->>Browser Client: Render Pink Lock Screen Gateway
    else is Authorized
        ShortUrlService->>DB: Thread-Safe Increment Click Counts
        ShortUrlService-->>HomeController: Return Original URL
        HomeController-->>Browser Client: Redirect 302 Found (Original URL)
    end
```

### 1. Active Route Protection & Reserved Keywords
Because ShortUrl redirects listen directly on the root path `/{code}`, an anonymous user could create a custom alias matching core system endpoints (e.g. `/api`, `/css`, `/js`, `/p`).
To secure routing integrity, the backend maintains a reserved keywords filter. Custom alias requests matching reserved routes are rejected with an explicit validation error.
Additionally, static file requests bypass database queries directly within the controller, optimizing database pool resources.

### 2. Live Analytics REST API
Users can track short link engagement metrics dynamically in real-time. Clicking the **Refresh Clicks** button makes an asynchronous AJAX fetch request to the server, refreshing the counter without reloading the page:
```java
@GetMapping("/api/url/shorten/{code}/info")
public ResponseEntity<?> getUrlInfo(@PathVariable String code) {
    return shortUrlService.getUrlInfo(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
```

### 3. Persistent Frontend Active States
To prevent the page from resetting back to the first tab (Scribble) on reload, the browser stores active state parameters in `localStorage`:
* Keeps track of the active tab.
* Saves the details of the last shortened link payload.
When the user refreshes, the UI reads the stored values and recreates the analytics cards, maintaining visual continuity.

---

## 🚀 Production Deployment Guide (100% Free Cloud Tier)

SortKut is engineered to run in a containerized environment connected to a serverless database, allowing for a completely **free and permanent** cloud hosting stack.

```mermaid
graph LR
    User([Public Traffic]) -->|1. Route Traffic| CF[Cloudflare CDN Proxy]
    CF -->|2. SSL & DDoS Filtering| Render[Render.com Free Web Service]
    Render -->|3. Query Data| Neon[(Neon Serverless Postgres)]
    UR[UptimeRobot Monitor] -->|4. Ping every 5 mins| Render
```

### Step 1: Set Up Serverless PostgreSQL (Neon)
Unlike Render's free PostgreSQL tier (which expires after 90 days), **Neon** provides serverless databases that remain free forever.
1. Sign up on [Neon.tech](https://neon.tech/) and launch a new database project named `SortKut`.
2. Copy your **Connection String** from the dashboard. It will look like:
   `postgresql://neondb_owner:npg_gNMPncwh4zF3@ep-winter-haze.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
3. Convert this string into Spring-friendly configuration details:
   * **JDBC URL:** `jdbc:postgresql://ep-winter-haze.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
   * **Username:** `neondb_owner`
   * **Password:** `npg_gNMPncwh4zF3`

### Step 2: Push Repository to GitHub
Render connects directly to your GitHub repository to build your applications.
```bash
git init
git add .
git commit -m "Configure SortKut for production release"
git branch -M main
git remote add origin https://github.com/your-username/SortKut.git
git push -u origin main
```

### Step 3: Deploy Container to Render
1. Register for an account on [Render.com](https://render.com/).
2. In the dashboard, click **New +** and select **Web Service**.
3. Select your linked GitHub repository.
4. Fill in the following properties in the configuration panel:
   * **Name:** `sortkut`
   * **Region:** (Select the region closest to your Neon database)
   * **Runtime:** `Docker` (Render automatically builds the [Dockerfile](file:///c:/Users/Admin/OneDrive/Documents/Projects/SortKut/Dockerfile) in the root of the workspace!)
   * **Instance Type:** `Free` ($0/month)
5. Open **Advanced Settings** and insert your production environment variables:
   * `SPRING_DATASOURCE_URL` = `jdbc:postgresql://ep-winter-haze.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
   * `SPRING_DATASOURCE_USERNAME` = `neondb_owner`
   * `SPRING_DATASOURCE_PASSWORD` = `npg_gNMPncwh4zF3`
6. Click **Create Web Service**. Render will build the container, package the application using Maven, and make the application live.

### Step 4: Prevent Sleep Mode & Cold Starts (UptimeRobot)
To prevent inactive containers from sleeping, a free monitoring service keeps the application warmed up:
1. Log in to [UptimeRobot.com](https://uptimerobot.com/).
2. Create a new monitor of type **HTTP(s)**.
3. Configure the destination URL to point to your live Render application (e.g., `https://sortkut.onrender.com`).
4. Set the **Monitoring Interval** to **every 5 minutes** and click Save.
5. UptimeRobot will ping the service regularly, maintaining rapid page loading speeds.

---

## 📈 Traffic & Storage Guardrails

### 💾 1. Disk Storage Protections
Because files are stored directly inside the container (`uploads/`), and Render's free tier has an ephemeral disk limit, we built active storage management into the codebase:
* **Scheduled Cleanup:** Expired or fully-downloaded files are swept automatically by the background thread.
* **Proactive Cleanup:** When a file's download limit is reached, it is immediately blocked and flagged for removal, keeping storage usage near zero.

### 🛡️ 2. Cloudflare Security Shield
To block bots, mitigate spam, and cache assets to reduce server load:
1. Connect your domain to a free **Cloudflare** account.
2. Set the DNS record to **Proxied (Orange Cloud)**.
3. Enable **DDoS Mitigation** and **Bot Fight Mode** in the Cloudflare dashboard. Static assets are cached globally, reducing server workload.

---

## 🛠️ Local Development & Setup

### Prerequisites
* **Java SDK:** 21 (LTS)
* **Maven:** 3.9+
* **Database:** PostgreSQL (or H2 in-memory profile)

### Run with Maven
1. Clone the project locally:
   ```bash
   git clone https://github.com/your-username/SortKut.git
   cd SortKut
   ```
2. Build and package the application:
   ```bash
   mvn clean package -DskipTests
   ```
3. Run the compiled JAR:
   ```bash
   java -jar target/SortKut-0.0.1-SNAPSHOT.jar
   ```
4. Open your browser and navigate to `http://localhost:8080`.

### Run with Docker Compose
If you have Docker installed, spin up the entire application stack:
```bash
docker-compose up -d --build
```
This launches the Spring Boot application and boots up an integrated, local PostgreSQL instance automatically.

---

## 📄 License
This project is open-source and licensed under the [MIT License](LICENSE).
