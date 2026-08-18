# 📦 Interview Cheat Sheet: The "Dropzone" Section

> This document is your step-by-step prep guide for tech interviews. It explains how you built the **Dropzone** (Send Anywhere-like file transfer) module of SortKut, written in **super simple, conversational English** so you can confidently explain it out loud under pressure.

---

## 🎙️ The 30-Second Elevator Pitch
*(Start with this when they ask you: "Tell me about this feature.")*

> *"I implemented **Dropzone** as a secure, anonymous file transfer utility. Users can drag and drop any file up to **1GB** to upload it. The system generates a unique **6-digit share code** and a **QR Code**. Anyone who keys in the code or scans the QR code on their phone can instantly find the file details and download it securely.
> 
> On the backend, it's a robust Spring Boot service that stream-writes files to a local directory (`uploads/`), tracks download limits (e.g. automatically blocking access after 1 download), and runs a scheduled background task to sweep and delete physical files from disk to keep storage clean."*

---

## 🛠️ Step-by-Step: How We Implemented Dropzone

Here is the exact order in which we built the feature, from the server settings up to the user interface:

### ⚙️ Step 1: Spring Server Upload Limit Configuration (`application.properties`)
* **What we did:** Spring Boot defaults to blocking file uploads larger than 1MB/10MB. To support sharing massive files, we configured Spring Boot’s multipart rules.
* **What we added:** We set `spring.servlet.multipart.max-file-size=1GB` and `spring.servlet.multipart.max-request-size=1100MB`.

### 🗄️ Step 2: The Database Blueprint (`FileTransfer.java`)
* **What we did:** Created a JPA Entity called `FileTransfer` mapping to the `file_transfers` table.
* **Fields we added:** An auto-incrementing ID, a unique 6-digit numeric `transferCode` (indexed for instant lookups), `fileName`, `fileSize` (in bytes), `mimeType`, `storagePath` (pointing to the actual file location on disk), `createdAt`, `expiresAt` timestamps, `downloadCount`, `maxDownloads` (download thresholds, e.g., 1 time), and an optional `password`.

### 🗃️ Step 3: The Database Communicator (`FileTransferRepository.java`)
* **What we did:** Created a repository extending Spring's `JpaRepository`.
* **Smart Decision:** We wrote a custom query `@Query` called `findPurgeableTransfers`. This query retrieves all files that are expired **OR** have reached their maximum download limit threshold so the cleaner can erase them.

### 🧠 Step 4: Core Brain & Local File Persistence (`FileTransferService.java`)
* **What we did:** This service manages file system operations, code generation, and sweeping.
* **What it handles:**
  1. Creates the `uploads/` directory inside the workspace if it's missing.
  2. Generates a unique 6-digit numeric code using `SecureRandom` and checks for collisions.
  3. Saves the file to disk with the code prepended (`uploads/{code}_{fileName}`) to prevent file-system conflicts.
  4. Increments the download count upon requests and blocks lookups immediately if the limit is reached.
  5. **Background Sweeper:** Activates a scheduled task waking up every 5 minutes (`0 */5 * * * *`) that scans for purgeable files, deletes them physically from disk, and removes the DB records.

### 🔌 Step 5: The REST API Controller (`FileTransferRestController.java`)
* **What we did:** Created endpoints mapping to `/api/transfer`.
* **What it handles:**
  * `POST /api/transfer`: Receives the multipart file stream and metadata, saves it, and returns the 6-digit code.
  * `GET /api/transfer/{code}`: Returns file metadata (size, name) if active. If password-protected, it requests a password before exposing the details.
  * `GET /api/transfer/{code}/download`: Stream-pipes the physical file from disk to the client as an attachment. 
  * **Smart Decision:** It parses the actual saved MIME Type (e.g. `image/png`) and outputs it in the `Content-Type` header so the browser knows exactly how to handle the downloaded file.

### 🎨 Step 6: Frontend JS & Tab Routing QR Code (`app.js` & `index.html`)
* **What we did:** Developed the client-side experience and mobile scanning logic.
* **What it handles:**
  * Uses the browser-side library `qrcode.js` to render a QR Code on success.
  * **Auto-Routing UX:** The QR Code contains the URL `/?code=XYZ`. When scanned by a phone, `app.js` detects the code, automatically navigates to the Dropzone tab, automatically switches to the "Receive File" panel, fills in the code, and triggers the search automatically!

---

## 💡 Key Concepts Explained in "Super Simple" Words

### 1. Spring Multipart Limits (The "Postal Envelope" Metaphor)
* **Interviewer asks:** *"What did you do to support 1GB files in Spring Boot?"*
* **Easy Answer:** *"By default, Spring Boot acts like a postal service that rejects any letter heavier than 1MB. We updated the configuration properties so that the server allows 'packages' up to 1GB to pass through Tomcat without triggering file-size exceptions."*

### 2. Physical File Deletion Race Conditions & Windows File Locks
* **Interviewer asks:** *"Why do you delete files in a background task instead of immediately in the download request?"*
* **Easy Answer:** *"We had a classic race condition: if we delete the file physically during the download request thread, we end up deleting the file **before** the browser has finished reading and downloading the bytes! The download fails with a 500 error, and the user gets a text error page.
> 
> Furthermore, on **Windows**, the operating system locks files that are open for reading. Trying to delete a file that is actively streaming will throw a file-lock exception. By incrementing the download count in the database (which blocks subsequent queries immediately), and letting a background task delete the physical file every 5 minutes, we ensure the download completes flawlessly and storage is reclaimed safely."*

### 3. Dynamic MIME Type vs. Generic Octet-Stream
* **Interviewer asks:** *"How did you fix files or images downloading in the form of text?"*
* **Easy Answer:** *"If you download a file with a generic `application/octet-stream` MIME type, the browser has to guess the format. Sometimes it defaults to text, which corrupts images or PDFs. I updated the controller to read the file's original MIME type from the database (e.g. `image/png` or `application/pdf`) and set it dynamically in the download headers. The browser knows the exact file format and downloads it perfectly."*

---

## 🎯 Interview Q&A Cheat Sheet

### Q1: *"How does your auto-lookup redirect work when a user scans the QR Code?"*
* **What to say:** *"To make the transition from PC to phone seamless, the QR Code contains a custom parameter: `http://domain/?code=123456`. On page load, the frontend checks if the `?code` parameter exists. If it does, it automatically switches tabs, populates the input, and clicks the search button. Once completed, it silently cleans the address bar using HTML5 `window.history.replaceState` so that page reloads don't re-trigger the lookup. It is an extremely clean, friction-free user experience."*

### Q2: *"Is streaming a 1GB file memory-safe in Spring Boot?"*
* **What to say:** *"Yes. We don't read the file bytes into the server's RAM (which would trigger `OutOfMemoryError`). Instead, we wrap the local file path inside Spring's `UrlResource` and stream it directly from the local disk stream. The memory footprint remains extremely light and flat regardless of file size."*
