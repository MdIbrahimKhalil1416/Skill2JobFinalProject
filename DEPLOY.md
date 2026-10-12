# Deploying to Render (with MongoDB Atlas)

Render does not offer MongoDB, so the database runs on a free **MongoDB Atlas** cluster. The app runs on Render using **Docker** (Render has no native Java runtime; the project includes a Dockerfile).

> Render and Atlas change their free-plan rules and menu names from time to time. If a step does not match what you see, check their own documentation.

---

## Step 1: MongoDB Atlas

1. Create an account at https://www.mongodb.com/cloud/atlas
2. Create a **free (M0) cluster**. Pick a nearby region such as Singapore or Mumbai
3. **Database Access** → add a database user (username and a strong password). Special characters such as `@ : / ? #` break the connection string, so letters and digits only is simplest
4. **Network Access** → add `0.0.0.0/0` (allow access from anywhere). Render's free plan has no fixed IP address, so this is required. This is also why a strong password matters
5. **Connect → Drivers** → copy the connection string. It looks like this:
   ```
   mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
   ```
6. Add the database name after `.net/`:
   ```
   mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/skill2job?retryWrites=true&w=majority
   ```
   This is your `MONGODB_URI`.

## Step 2: Push the code to GitHub

```bash
cd skill2job
git init
git add .
git commit -m "Skill2Job initial version"
git branch -M main
git remote add origin https://github.com/<your-username>/skill2job.git
git push -u origin main
```
`.gitignore` already excludes `.env` and `target/`, so secrets and build output are not pushed. **Never write a real password or connection string into the code.**

## Step 3: Create a Web Service on Render

1. Sign in at https://render.com with GitHub
2. **New → Web Service** → select your `skill2job` repository
3. Settings:
   - **Runtime / Language:** Docker
   - **Branch:** main
   - **Instance type:** Free
   - **Health Check Path:** `/actuator/health`
4. Add these **Environment Variables**:

**Required** - the app refuses to start (with a message listing what is wrong) if any of these is missing or unsafe:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `MONGODB_URI` | The URI from step 1 |
| `JWT_SECRET` | A random string of at least 32 characters (Render can generate it). Placeholders such as "change-me" and short or repetitive values are rejected |
| `ADMIN_EMAIL` | Your admin email. Used only to create the FIRST admin; an existing account is never changed |
| `ADMIN_PASSWORD` | 10+ characters with letters and digits, not a known default. Used only when the admin does not exist yet |

**Needed for the site to work for real users** (the app starts without them but logs a warning):

| Key | Value |
|---|---|
| `APP_BASE_URL` | Your public address, e.g. `https://skill2job.onrender.com` (payment redirects and email links use it) |
| `BREVO_API_KEY` | Brevo API key (see "Email on Render" below). Render's free plan blocks SMTP ports, so this is how signup codes, password resets and alerts get delivered |
| `MAIL_USERNAME` (or `MAIL_FROM`) | The sender address. With Brevo it must be the sender email you verified in Brevo. `MAIL_PASSWORD` is only needed for SMTP (local development) |

**Optional** (safe defaults):

| Key | Default | Meaning |
|---|---|---|
| `SEED_ENABLED` | `false` in production | Demo users/jobs/courses. Production ignores `true` unless `SEED_ALLOW_IN_PROD=true` is also set - do not do that on a real deployment |
| `SSLCOMMERZ_SANDBOX` | `true` | Sandbox = no real money. `false` (live) is accepted only with your own `SSLCOMMERZ_STORE_ID` / `SSLCOMMERZ_STORE_PASSWORD` and an `https://` `APP_BASE_URL` |
| `SSLCOMMERZ_STORE_ID`, `SSLCOMMERZ_STORE_PASSWORD` | public sandbox values | Your own store credentials |
| `CLIENT_IP_HOPS` | `1` | Reverse proxies in front of the app (Render = 1). Used only for per-IP rate limits on login/signup/OTP/forgot-password. If many users get "Too many requests" at once, Render is sending a different proxy chain: try `2`, or `0` to ignore the header |
| `RATE_LIMIT_ENABLED` | `true` | Per-instance limits on the public auth endpoints |
| `OTP_RESEND_COOLDOWN_SECONDS`, `OTP_MAX_ATTEMPTS`, `OTP_EXPIRY_MINUTES` | `60`, `5`, `5` | Signup code rules (also used for password-reset codes) |
| `CORS_ORIGINS`, `JOB_POST_FEE`, `SUBSCRIPTION_PRICE`, ... | see `.env.example` | |

Do not set `PORT`. Render provides it and the app uses it automatically.

5. Click **Create Web Service**. The first build can take 5-10 minutes while Maven downloads dependencies.

> Alternative: the repository contains `render.yaml`. In Render, choose **New → Blueprint** and select the repository. The service is created automatically; you only enter `MONGODB_URI`, `ADMIN_EMAIL` and `ADMIN_PASSWORD` (Render generates `JWT_SECRET`).

## Step 4: Verify

- `https://<your-app>.onrender.com/actuator/health` → `{"status":"UP"}`
- `https://<your-app>.onrender.com` → landing page
- `https://<your-app>.onrender.com/swagger-ui.html` → API documentation
- Log in with a demo account (see README). If you do not want demo data, set `SEED_ENABLED=false`

**Logs:** Render dashboard → your service → **Logs** tab.

## Uploaded files (CVs and company logos): read this before going live

Render's **free plan has no persistent disk.** CVs and logos are written to `/data/cv` and `/data/logos`
inside the container. That folder is **wiped on every redeploy, restart after idle, or instance replacement**.
What happens then:

- The database still says "this student has a CV", but the file is gone. The app does not crash: the first
  download returns 404 "no longer available, upload again" and the stale reference is cleared automatically
  (same for logos). Nothing is silently corrupted, but **the files themselves are lost**.
- So on the free plan treat uploads as **demo-only**.

Options when real users matter (smallest first):

1. **Render persistent disk** (paid plan, a few USD/month): add a disk mounted at `/data`, nothing else changes.
   The container runs as a non-root user; the Dockerfile already `chown`s `/data`, but a freshly mounted disk
   arrives owned by root - if the app fails at startup with "upload directory is not writable", run once
   `chown -R <app uid> /data` from a Render shell, or set the disk mount to a path the app can write.
   One instance only (a disk cannot be shared between instances).
2. **MongoDB GridFS** (no new provider; uses the Atlas cluster you already have). Atlas free tier is 512 MB,
   so keep the CV size limit small. This needs a code change (store bytes in GridFS instead of a folder) and
   is **not implemented yet** - say the word and it can be done behind the existing `store/find/delete` methods.
3. An external object store (S3/Cloudflare R2/Cloudinary) - only if you outgrow the two above.

Orphaned files (an upload whose database save failed, a crash half-way) are removed daily at 03:30 Dhaka time
by `StorageCleanupScheduler`, only if older than 24 h and referenced by no profile. Controls (all optional):
`STORAGE_CLEANUP_ENABLED` (default true), `STORAGE_CLEANUP_MIN_AGE_HOURS` (default 24).

## Email on Render (Brevo)

> **Email is optional.** Email verification at signup is OFF by default (`EMAIL_VERIFICATION_ENABLED=false`): new
> accounts are active immediately and users are logged in right after signing up, so the site works without any
> email provider. Without email, "Forgot password" codes, job-alert emails and status emails are simply not sent.
> To require the emailed code again, set `EMAIL_VERIFICATION_ENABLED=true` together with `BREVO_API_KEY`.

Render's free web services block outbound SMTP ports (25/465/587), so Gmail SMTP cannot send from there. The app
therefore sends email through **Brevo's HTTPS API** whenever `BREVO_API_KEY` is set (otherwise it uses SMTP, which
is fine locally). No other switch is needed.

1. Create a free account at https://www.brevo.com
2. **Senders, domains & dedicated IPs → Senders → Add a sender**: enter the address emails should come from (your
   Gmail is fine) and confirm the link Brevo emails to it. No own domain is required for a single verified sender.
3. **SMTP & API → API keys → Generate a new API key**. Copy it once (it is shown only once).
4. On Render: Environment → add `BREVO_API_KEY` = that key. Make sure `MAIL_USERNAME` (or `MAIL_FROM`) is exactly the
   verified sender address. `MAIL_PASSWORD` can be removed.
5. Redeploy and sign up with a test address. If no code arrives, the log line "Brevo refused the email ... HTTP 401"
   means a wrong key, "HTTP 400 ... sender" means the sender is not verified. The key, the code and the email text are
   never written to the logs.

Gmail may put mail from a free sender in Spam the first time; mark it "Not spam".

## Things to remember
- On the free plan, the app sleeps after about 15 minutes without traffic. The next request takes around a minute to wake it. **Open the link once before your demo or viva.**
- A free instance has 512 MB of RAM. The Dockerfile limits the Java heap to fit.

---

## Troubleshooting

| Problem (what you see in the logs) | Cause and fix |
|---|---|
| "Skill2Job could not connect to MongoDB" / `MongoTimeoutException` | `0.0.0.0/0` is missing from Atlas Network Access, or the URI is wrong. Allow the IP in Atlas and copy the URI again |
| "MongoDB rejected the username or password" / `bad auth` | Wrong database username or password, or the password contains special characters. Reset it in Atlas to letters and digits only |
| `JWT_SECRET must be at least 32 characters long` | `JWT_SECRET` is too short. Make it longer |
| `No open ports detected` / deploy fails | The app crashed. Read the first error near the top of the logs (usually the MongoDB connection) |
| `Out of memory` / repeated restarts | The free plan ran out of RAM. Try `-XX:MaxRAMPercentage=60` in `JAVA_OPTS` |
| Build fails with `release version 25 not supported` | If the Docker image does not provide JDK 25, set `java.version` to 21 in `pom.xml` and change `temurin-25` to `temurin-21` in the Dockerfile |
| Pages load but have no styling | The browser cannot load the Tailwind CDN (internet or firewall). Try another network |
| "Your session ended" right after logging in | Changing `JWT_SECRET` invalidates old tokens. Log in again |
