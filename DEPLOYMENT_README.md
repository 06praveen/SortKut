# 🚀 SortKut Production Deployment Guide (100% Free Tier)

This guide walks you through deploying **SortKut** to the cloud completely for **free**, ensuring it is highly available, runs securely, and manages storage limits automatically so that you do not incur charges or run out of disk space under traffic.

---

## 🏗️ The Free Deployment Architecture

To host a Java/Spring Boot application with PostgreSQL for free, the ultimate stack is:
1. **Neon (neon.tech)**: Serves a **100% Free Serverless PostgreSQL Database** (unlike Render's free PostgreSQL which expires after 90 days, Neon is free forever).
2. **Render (render.com)**: Hosts the Spring Boot application using the root [Dockerfile](file:///c:/Users/Admin/OneDrive/Documents/Projects/SortKut/Dockerfile) completely for free.
3. **UptimeRobot (uptimerobot.com)**: A free monitor that pings your site every 5 minutes to prevent Render's free tier from falling asleep (solving the "cold start" delay).

---

## 🗄️ Step 1: Set Up Your Free PostgreSQL (Neon)

1. Go to [Neon.tech](https://neon.tech/) and sign up for a free account.
2. Click **Create Project**, name it `SortKut`, and select your database region (choose the one closest to you).
3. Under the **Dashboard**, copy your database **Connection String**. It will look similar to this:
   `postgresql://neondb_owner:password@ep-cool-breeze-12345.aws.neon.tech/neondb?sslmode=require`
4. Parse the connection details:
   * **URL:** `jdbc:postgresql://ep-cool-breeze-12345.aws.neon.tech/neondb?sslmode=require`
   * **Username:** `neondb_owner`
   * **Password:** `password`

---

## 🐳 Step 2: Push Your Project to GitHub

Render builds your application directly by connecting to your GitHub repository.
1. Create a **Private or Public Repository** on GitHub named `SortKut`.
2. Initialize git in your local project folder and push the code:
   ```bash
   git init
   git add .
   git commit -m "Initialize SortKut codebase"
   git branch -M main
   git remote add origin https://github.com/your-username/SortKut.git
   git push -u origin main
   ```

---

## 🚀 Step 3: Deploy to Render (Web Service)

1. Go to [Render.com](https://render.com/) and sign up for a free account.
2. Click the **New +** button in the dashboard and select **Web Service**.
3. Select **Build and deploy from a Git repository**, and connect your GitHub account.
4. Select your `SortKut` repository.
5. In the Web Service configuration form, enter the following parameters:
   * **Name:** `sortkut`
   * **Region:** (Match the region you selected on Neon)
   * **Branch:** `main`
   * **Runtime:** **Docker** (Render will automatically detect the [Dockerfile](file:///c:/Users/Admin/OneDrive/Documents/Projects/SortKut/Dockerfile) we created in the root and build it cleanly!)
   * **Instance Type:** **Free** ($0/month)
6. Scroll down and click **Advanced**, then click **Add Environment Variable**. Add your database credentials:
   * `SPRING_DATASOURCE_URL` = `jdbc:postgresql://ep-cool-breeze-12345.aws.neon.tech/neondb?sslmode=require`
   * `SPRING_DATASOURCE_USERNAME` = `neondb_owner`
   * `SPRING_DATASOURCE_PASSWORD` = `password`
7. Click **Create Web Service**. 

*Render will spin up a container, automatically build your Spring Boot app using Maven, package it, and boot it on port 8080. When complete, it will provide your live URL (e.g. `https://sortkut.onrender.com`).*

---

## ⏱️ Step 4: Prevent Cold Starts (UptimeRobot)

**The Gotcha:** Render's free tier spins down (goes to sleep) if the web app receives no traffic for 15 minutes. When a new user visits, it takes ~50 seconds for Render to wake up (a "cold start" delay).

**The Solution:**
1. Go to [UptimeRobot.com](https://uptimerobot.com/) and create a free account.
2. Click **Add New Monitor**.
3. Set **Monitor Type** to `HTTP(s)`.
4. Name it `SortKut Wakeup`.
5. Enter your Render live URL (e.g. `https://sortkut.onrender.com`).
6. Set the **Monitoring Interval** to **every 5 minutes**.
7. Save the monitor.

*UptimeRobot will ping your server every 5 minutes. This minor request acts as active traffic, keeping your Render container warm and responsive 24/7 with **zero cold starts**!*

---

## 📈 Managing Traffic & Storage Safely (Free Tier Guardrails)

### 💾 1. Disk Storage Protections
Since files are uploaded to the local container storage (`uploads/`), and Render’s free tier has a 10GB ephemeral disk limit, we built two crucial guardrails into SortKut:
* **The Background Sweeper:** Our scheduled cleaner wakes up every 5 minutes, automatically scans the database for expired or fully-downloaded files, and deletes them physically from the disk. This keeps active storage usage near zero.
* **Immediate Deletion on Download Limit:** Once a file reaches its maximum download boundary (e.g., `1` download), the database instantly blocks further access, and the background task sweeps it immediately.

### 🛡️ 2. DDOS & Traffic Protection (Cloudflare)
If you expect high traffic or want to prevent spam:
1. Route your custom domain through **Cloudflare** (100% free account).
2. Enable the **Cloudflare Proxy (Orange Cloud)**.
3. Turn on **DDoS Protection** and **Bot Fight Mode** under Cloudflare's security dashboard.
This filters out malicious scanners, throttles abusive IPs, and caches static resources (CSS, JS, images) at Cloudflare's edge, meaning your free Render server only processes actual application requests, reducing CPU load to a fraction.
