# 🔗 Interview Cheat Sheet: The "Shrink" Section

> This document is your step-by-step prep guide for tech interviews. It explains how you built the **Shrink** (Bit.ly-like URL shortener) module of SortKut, written in **super simple, conversational English** so you can confidently explain it out loud under pressure.

---

## 🎙️ The 30-Second Elevator Pitch
*(Start with this when they ask you: "Tell me about this feature.")*

> *"I implemented **Shrink** as a high-performance URL shortener. Users paste a long URL, can choose an optional custom alias (like `my-link`), set an expiration time, and add optional password protection. The system outputs a unique short link with a QR code and includes an interactive click-statistics panel.
> 
> Technically, it utilizes a custom indexed database for $O(1)$ redirect lookups, session-level gates for password-protected links, state caching in browser local storage, and a REST endpoint to query link clicks in real-time."*

---

## 🛠️ Step-by-Step: How We Implemented Shrink

Here is the exact order in which we built the feature, from the database up to the user interface:

### 🗄️ Step 1: The Database Blueprint (`ShortUrl.java`)
* **What we did:** Created a JPA Entity called `ShortUrl` mapping to the `short_urls` table.
* **Fields we added:** An auto-incrementing ID, a unique `shortCode` (indexed for instant lookups), `originalUrl` (length 2048 to support long tracking queries), `title`, `password` hash, `createdAt`, `expiresAt`, `clickCount`, `clicksToday`, and `lastClickAt` timestamps.
* **Smart Decision:** We declared a unique index on the `shortCode` column.

### 🗃️ Step 2: The Database Communicator (`ShortUrlRepository.java`)
* **What we did:** Created a repository extending Spring's `JpaRepository`.
* **Smart Decision:** Added a custom transaction sweep query `@Query` to clean up all expired links in a single database round-trip.

### 🧠 Step 3: Core Brain & Alias Rules (`ShortUrlService.java`)
* **What we did:** This service manages redirect lookups, alias validations, and stats incrementing.
* **What it handles:**
  1. **Random Code:** Generates unique 6-character short codes using `SecureRandom` and runs database exister loops to guarantee zero collisions.
  2. **Custom Alias:** Validates custom aliases against a strict regex (`^[a-zA-Z0-9_-]+$`) and blocks reserved keywords (e.g. `api`, `p`, `css`, `js`) so users can't disrupt core system routing paths.
  3. **Clicks increment:** Thread-safely increments total clicks, clicks today, and records the current timestamp.

### 🔌 Step 4: The REST Controllers (`UrlRestController.java` & `HomeController.java`)
* **What we did:** Implemented both the JSON creation API and the physical routing controllers.
* **What they handle:**
  * `@PostMapping("/api/url/shorten")`: Validates requests, registers the URL, and returns the short code details.
  * `@GetMapping("/api/url/shorten/{code}/info")` **[Stats Query API]**: Exposes the current clicks and timestamps in real-time.
  * `@GetMapping("/{code}")` **[Redirection Interceptor]**: Listens directly on the root path. When hit, it checks the database:
    * If missing or expired, it shows a friendly "Expired" screen.
    * If password-protected, it checks the session key (`HttpSession`) and routes the user to a pink security gateway card if unauthorized.
    * If authorized, it calls `incrementClicks` and returns `redirect:{originalUrl}`.

### 🎨 Step 5: Frontend Experience & Local Storage Caching (`index.html` & `app.js`)
* **What we did:** Developed the visual components and persistent page states.
* **What it handles:**
  * **"Refresh Clicks" Button:** Added a button in the results card. Clicking it makes an asynchronous AJAX request to `/api/url/shorten/{code}/info` and dynamically updates the clicks stats on-screen.
  * **localStorage Preservation:** Saves the active tab name and the last shortened URL response payload in local storage. When you reload the page, you stay on the Shrink tab, and the shortened result box remains fully visible!

---

## 💡 Key Concepts Explained in "Super Simple" Words

### 1. Database Indexing (The "Post Office Box" Metaphor)
* **Interviewer asks:** *"Why did you index the shortCode column?"*
* **Easy Answer:** *"Because the database handles thousands of redirection requests per second, searching the table row-by-row would create a major bottleneck. Indexing the `shortCode` column acts like giving the post office a direct box number ($O(1)$ lookup time) instead of making the mailman search every single house in the city ($O(N)$)."*

### 2. Session-Level Password Caching
* **Interviewer asks:** *"How do you authorize password-protected links?"*
* **Easy Answer:** *"When a user inputs the correct password, we save an `unlocked_url_{code}` flag in their browser's HTTP Session. When they redirect through that link again, the server reads the session cookie and lets them pass silently. This keeps the experience secure but friction-free since they don't have to re-type the password every time they visit the link during that session."*

### 3. localStorage Caching
* **Interviewer asks:** *"Why did you save the shortener state in localStorage?"*
* **Easy Answer:** *"In a standard Single Page Application layout, reloading the page wipes out the client-side state, resetting the user to the Scribble tab. By saving the active tab and the last shortened link payload in `localStorage`, we can automatically restore the tab and display the stats card upon page refresh. The user gets a continuous experience."*

---

## 🎯 Interview Q&A Cheat Sheet

### Q1: *"What happens if a user requests a custom alias that clashes with a system route, like '/api'?"*
* **What to say:** *"This is a classic system vulnerability where a user could hijack core backend routes. I solved this by defining a strict set of `RESERVED_KEYWORDS` (such as `api`, `p`, `css`, `js`) in `ShortUrlService`. Any request to use a reserved word as a custom alias is instantly rejected, protecting our core routing integrity."*

### Q2: *"How do you prevent redirect requests from clashing with static files like '/css/style.css'?"*
* **What to say:** *"In `HomeController.java`, the root redirect mapping `GET /{code}` starts with a bypass validation loop. If the `{code}` is a static file or system route, it forwards the request directly to the default resources handler instead of querying the database, avoiding database load and routing errors."*
