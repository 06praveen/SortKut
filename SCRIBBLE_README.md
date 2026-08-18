# 📝 Interview Cheat Sheet: The "Scribble" Section

> This document is your step-by-step prep guide for tech interviews. It explains how you built the **Scribble** (Pastebin-like) module of SortKut, written in **super simple, conversational English** so you can confidently explain it out loud under pressure.

---

## 🎙️ The 30-Second Elevator Pitch
*(Start with this when they ask you: "Tell me about this feature.")*

> *"I implemented **Scribble** as an anonymous, zero-friction text and code sharing tool. Users can paste code snippets, pick a programming language for syntax highlighting, select an expiration time (e.g., 24 hours), and optionally set a password. It generates a unique, shareable short link and a QR code instantly. 
> 
> Technically, it is a robust Spring Boot micro-service with an indexed database for fast lookups, cryptographically secure short URLs, and a scheduled background cleaner that automatically purges expired snippets to keep the database lightweight."*

---

## 🛠️ Step-by-Step: How We Implemented Scribble

Here is the exact order in which we built the feature, from the database up to the user interface:

### 🗄️ Step 1: The Database Blueprint (`Paste.java`)
* **What we did:** We created a database model (Entity) called `Paste` using Hibernate/JPA.
* **Fields we added:** An auto-incrementing database ID, a unique 7-character string called a `slug` (which acts as the short URL), the actual text/code `content`, the programming `language`, an optional `password`, `createdAt`, and `expiresAt` timestamps.
* **Smart Decision:** We told the database to **Index** the `slug` column. 

### 🗃️ Step 2: The Database Communicator (`PasteRepository.java`)
* **What we did:** We created a repository interface extending Spring’s `JpaRepository`.
* **What it handles:** It gives us built-in methods to save, find, and delete pastes.
* **Smart Decision:** We wrote a custom query `@Query` to bulk-delete expired pastes in one go.

### 🧠 Step 3: Core Brain & Business Logic (`PasteService.java`)
* **What we did:** This service acts as the traffic controller for all calculations.
* **What it handles:** 
  1. Generates the 7-character short link using `SecureRandom`.
  2. Runs a loop checking if that link already exists in the database to prevent duplicate collisions.
  3. Calculates the exact expiration date (e.g., if a user selects `1h`, it adds 1 hour to `now`).
  4. Activates a **Scheduled Cron Job** that automatically wakes up every 5 minutes to sweep the database and delete expired pastes.

### 🔌 Step 4: The REST API (`PasteRestController.java`)
* **What we did:** Created a REST Controller that listens to web requests.
* **What it handles:** It accepts the text data submitted by the frontend, runs it through `PasteService`, saves it to the database, and returns the short code slug back to the user as a JSON response.

### 🏠 Step 5: Web Page Routing & Security Gateway (`HomeController.java`)
* **What we did:** This controller handles what page the viewer sees when they click a shared link (`/p/{slug}`).
* **What it handles:**
  1. **Expiry Block:** If someone visits a link but the expiration time has passed, it immediately blocks access and shows an "Expired" message.
  2. **Password Lock:** If a paste is password-protected, it stops the page from loading and shows a sleek lock screen.
  3. **Session Memory:** When the user enters the correct password, it saves a tiny "unlocked" stamp in their browser session. This way, if they refresh the page, they don't have to type the password again.

### 🎨 Step 6: The Frontend User Experience (`index.html` & `app.js`)
* **What we did:** Crafted a premium user interface with interactive elements.
* **What it handles:**
  * Uses browser-native APIs to copy share links to the user's clipboard in one click.
  * Uses a client-side library (`qrcode.js`) to turn the share link into a clean QR code.
  * Uses `Prism.js` to highlight code keywords (like functions, strings, and variables) so it looks like a professional text editor.

---

## 💡 Key Concepts Explained in "Super Simple" Words
*(Use these exact metaphors when an interviewer asks "Why did you do it this way?")*

### 1. Database Indexing (The "Book Index" Metaphor)
* **Interviewer asks:** *"Why did you index the slug column?"*
* **Easy Answer:** *"Without an index, if a user goes to a link, the database has to read every single row from the very beginning to find it (which is $O(N)$). By indexing the `slug` column, the database creates an sorted alphabetized list on the side. Finding the slug becomes instant (constant time, or $O(1)$) because the database knows exactly where to jump, just like looking up a word in the index at the back of a textbook."*

### 2. Secure Random vs. Normal Random
* **Interviewer asks:** *"Why did you use SecureRandom to generate slugs instead of just sequential numbers or standard Random?"*
* **Easy Answer:** *"If we used sequential IDs (like `paste/1`, `paste/2`), a hacker could easily write a simple script to download every single paste in order. If we used standard `java.util.Random`, the numbers generated follow a predictable math formula, allowing smart attackers to guess future links. By using `SecureRandom`, we use cryptographically secure entropy, making it mathematically impossible to guess or predict the next generated link."*

### 3. Smart Expiry (Double-Gate Security)
* **Interviewer asks:** *"How do you handle expiration? What if your background cleaner hasn't run yet but the post is expired?"*
* **Easy Answer:** *"We use a **double-gate security model**. First, we have a background cleaner running every 5 minutes to delete old files. But to make sure a user *never* reads an expired paste in that 5-minute gap, our retrieval code check also verifies the time. Even if the expired paste is still sitting in the database waiting to be cleaned, the code actively rejects it and treats it as 'Not Found' if the current time is past the expiry stamp."*

### 4. Session Gating
* **Interviewer asks:** *"How does your password verification work?"*
* **Easy Answer:** *"We keep password verification simple and secure using HTTP Session. When a user submits the correct password for a paste, the server stamps a cookie in their browser session saying 'Authorized'. On subsequent refreshes, the server reads this session stamp and lets them view the paste. The moment they close the browser, the session is wiped out, keeping the private note secure."*

---

## 🎯 Interview Cheat Sheet: Standard Q&A

### Q1: *"What happens if two people generate the same 7-character code at the same millisecond?"*
* **What to say:** *"Since we use a cryptographically strong character set of 62 letters and numbers, the number of total possible combinations is $62^7$, which is about 3.5 trillion. The mathematical odds of a collision are practically zero. However, to make the system bulletproof, I wrapped the generation inside a `do-while` loop. The code queries the database first; if the slug is already taken, it discards it and generates a new one. This ensures absolute collision-safety under heavy write loads."*

### Q2: *"Why did you decide to use standard Session cookies instead of JWT (JSON Web Tokens)?"*
* **What to say:** *"Since SortKut is designed to be an account-free, zero-friction utility, we don't have user signups or long-term logins. JWTs are great for distributed, stateless authentication where users have accounts. For this project, standard session cookies are simpler, highly secure for transient data, and don't require the client to store and manage tokens in localStorage."*

### Q3: *"How does this system scale if you have 10 million active users?"*
* **What to say:** *"If we scaled up, I would make three simple changes:*
  1. *Move session storage from server memory to an external **Redis** cache so that if one server goes down, the user stays authenticated.*
  2. *Use **Redis** to cache public, high-traffic pastes so we don't hit the database for every single read.*
  3. *Instead of querying the database inside a `do-while` loop to check for slug collisions, I would use a pre-allocated distributed ID generator (like Snowflake) to generate unique, secure short codes offline."*
