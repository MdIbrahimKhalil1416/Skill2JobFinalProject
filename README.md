# Skill2Job: Student Job Portal with Skill-gap Analysis

Skill2Job is a Spring Boot web application. Students add their skills with a level from 1 to 5. Employers post jobs and specify how much of each skill they need. For every job, the system calculates a **match percentage**, shows which skills are **missing, weak or strong**, and **suggests courses** to close the gap.

> The matching is **rule-based and weighted**, not AI or machine learning. Say this clearly in your viva.

---

## Tech Stack

| Part | Technology |
|---|---|
| Language | Java 25 (also runs on JDK 21 if you change `java.version` in `pom.xml`) |
| Framework | Spring Boot 3.5 (Web, Data MongoDB, Security, Validation, Actuator, Cache, Mail) |
| Database | MongoDB (local or MongoDB Atlas) |
| Authentication | JWT (jjwt 0.12) with BCrypt password hashing; signup also requires email OTP verification |
| Email | Brevo HTTPS API when `BREVO_API_KEY` is set (production on Render), otherwise `JavaMailSender` (SMTP): signup OTP, application status changes, deadline reminders, interview proposals |
| File storage | Local disk (configurable directories) for uploaded CVs and employer logos |
| Payments | SSLCommerz (sandbox) - employer job-posting fee + Pro subscription |
| API documentation | Swagger UI (springdoc-openapi) |
| Frontend | HTML, vanilla JavaScript, Tailwind CSS (CDN), Chart.js, a small custom i18n engine (EN/বাং) |
| Tests | JUnit 5 |
| Deployment | Docker, Render, MongoDB Atlas |

## UI, language and the About page

- **Theme**: a single dark, neon-purple theme (`css/app.css` + `js/tw-config.js`) - glowing gradient
  hero text, a "circuit floor" grid decoration, glow-ring icons and glow effects on buttons/pips/match
  badges. There is no light-mode toggle; this is the one theme the app ships with.
- **Language switcher**: an EN / বাং toggle in the top navigation (desktop and mobile), backed by
  `js/i18n.js` - a plain dictionary keyed by `data-i18n="some.key"` attributes, with the choice
  remembered in `localStorage` and re-applied on every page via a shared `apply()` pass. Coverage
  spans every page's static chrome: navigation, footer, landing, About, and the headings, labels,
  buttons, hints and placeholders of the dashboard, profile, jobs, applications, interviews, chat,
  admin, employer and auth pages. Per-record data coming from the API (job titles, company names,
  chat messages, a user's own name) is never translated, since that is the installation's real data,
  not UI text - only the labels around it switch language. A page whose heading depends on that kind
  of live data (e.g. the student dashboard's "Hi {name}, ...") re-renders itself through
  `SB_I18N.t()` and listens for the `sb:langchange` event instead of using a plain `data-i18n`
  attribute, so the name and the translated template combine correctly either way. Extending
  coverage further is just adding `data-i18n="..."` (or `data-i18n-placeholder="..."`) attributes to
  a page's HTML and a matching key to both dictionaries in `i18n.js` - nothing else changes.
- **About page** (`about.html`): mission, feature overview and a "why we built it this way" section.
  Its stat cards (open jobs, skills in the catalog, courses linked) are **live counts fetched from
  this installation's own API**, not hardcoded marketing numbers - so they stay honest on a fresh
  database with different seed data.

## Features

**Student**
- Register with email verification: a 6-digit OTP is emailed on signup and the account stays
  inactive until it is confirmed (see "Email Verification" below)
- Optionally upload a CV (PDF/DOC/DOCX, max 5 MB) during signup, or any time after from "My skills"
  (drag-and-drop dropzone with **replace** and **delete** actions, not just the initial upload);
  download it later, and employers can download it for students who applied to their jobs
- Log in, and edit a profile (department, CGPA, graduation year, bio)
- Add skills with a level (1-5). Typing "ReactJS" is recognised as "React" through aliases
- **Portfolio links**: GitHub, LinkedIn and personal website links shown on the profile
- **Course progress tracker**: mark a suggested course "In Progress" or "Completed"; a small
  progress bar (50% / 100%) is shown on the profile
- **Skill endorsement badges**: an admin, or another student, can endorse one of a student's
  skills (a boolean flag with who endorsed it and when, shown as a badge, like LinkedIn)
- **Bookmarked/saved jobs**: save a job to look at or apply to later, from the job list or the job page
- **In-app deadline reminders**: once a day (and immediately at startup for the demo), a scheduler
  checks bookmarked jobs and notifies + emails the student when the deadline is 2 days away or less
- **Side-by-side job comparison**: pick up to 4 jobs from the job list and compare their required
  skills, type, location and deadline on one page
- See all open jobs ranked by match percentage
- For each job: a skill-gap report (missing / weak / strong) and course suggestions
- Apply to jobs. If a required skill is missing, the student is warned and can choose "Apply anyway"
- Track applications and their status, receive in-app notifications and status-change emails
- **In-app chat** with the employer, once an application is shortlisted or hired
- **Interview scheduling**: pick one of the time slots an employer proposed for an interview
- Dashboard: average match, number of eligible jobs, and a chart of the most frequently missing skills

**Employer**
- Company profile, including **company branding**: a logo upload and a longer "About us" page,
  shown on a public company page any visitor can open from a job listing
- Post, edit, close, reopen and delete jobs, with a minimum level, weight and "required" flag for each skill
- **Reusable job templates**: save a job's skill set and basic details as a template, then prefill a
  new job post from it later instead of retyping everything
- Applicant list sorted by match percentage, with **filtering by minimum match %, department and
  minimum CGPA**
- **Bulk shortlist/reject**: select several applicants and move them all to the next status in one action
- Application workflow: Applied → Shortlisted → Hired (or Rejected), with an automatic email to the
  applicant on every status change (acceptance/rejection/shortlist wording differs by outcome)
- **Interview scheduling**: propose one or more date/time slots for an applicant; the student picks one
- **In-app chat** with a shortlisted or hired applicant
- Download the CV of any student who applied to one of their jobs
- Dashboard with an applicants-per-job chart and **hiring-funnel analytics**: Applied → Shortlisted →
  Hired conversion rates and the average number of days from application to hire
- **Payments (job-posting fee + Pro subscription)**: a limited number of job posts are free
  (`FREE_JOB_POSTS`, default 2 per employer, lifetime); after that, a new job is created
  `PENDING_PAYMENT` and only becomes publicly visible once a one-time SSLCommerz payment succeeds -
  or an employer can buy a time-limited **Pro** subscription that waives the fee entirely while
  active. See "Payments" below



**Admin**
- Manage skills, including aliases (a skill that is in use cannot be deleted)
- Manage courses
- Activate or deactivate users
- Delete any job (cascades to its applications, bookmarks, interviews and chat messages)
- **Endorse a student's skills** from a dedicated panel on the admin page (see "Skill endorsements" below)
- Dashboard built with MongoDB aggregation: most demanded skills, users by role, jobs per month

**Communication**
- Real SMTP email beyond the signup OTP: application status changes, job-deadline reminders for
  bookmarked jobs, and interview-proposed/interview-scheduled notices - each with its own
  auto-generated template (see `EmailService`), always best-effort so a down mail server never blocks
  the action that triggered it
- In-app chat, scoped to one job application, between the employer and the applicant once the
  application is shortlisted or hired

**Career tools (new)**
- **Skill tests (quiz module):** a 5-question MCQ test per skill (96 seeded questions across 16 skills).
  Questions are drawn and fixed on the server, the correct answers never reach the browser before
  submission, and an unfinished attempt is resumed instead of re-rolled. 60%+ verifies the skill
  (`StudentSkill.verified`, level 2-5 from the score: 40/60/75/90%); a "Verified" badge then shows on the
  profile, to employers and in analytics. Retake cooldown and time limit are configurable.
- **Job alerts (saved searches):** keyword / type / location / skill / minimum-match filters, up to 10
  per student. A `@Scheduled` job (every 15 min) finds newly published matching jobs, sends an in-app
  notification and an optional email digest. Each check covers only the window since the previous
  check, so the same job is never sent twice. "Save this search as an
  alert" on the Jobs page pre-fills the form.
- **Learning roadmap generator:** for a chosen job (or the market's most demanded skills) it uses the
  existing match calculator to find gaps, picks courses in difficulty order (max 3 per skill, completed
  ones skipped), packs them into weeks by your hours-per-week, adds a skill test after each skill and
  shows current vs projected match.
- **CV builder + PDF export:** profile, verified skills and completed courses fill in automatically; add
  projects, experience, education, certificates and languages; 3 templates; download as PDF
  (html2pdf.js) or save it as the CV employers download.
- **Mock interview practice:** 54 seeded questions across 18 skills. Write your answer, rate yourself,
  get rule-based feedback on which key points you covered (stemmed keyword matching). Score = 70%
  coverage + 30% self-rating. Designed so an AI reviewer can be plugged in later.
- **Gamification:** XP for learning actions, levels, daily learning streak (Asia/Dhaka days), 15 badges,
  an 11-item profile-completeness checklist, and a top-20 leaderboard (first name + initial only).
- **Company reviews & ratings:** students who were shortlisted/rejected/hired/interviewed can rate a
  company (1-5 stars, pros/cons, optional anonymity). Reviews are PENDING until an admin approves them;
  approved ones appear on the company page, job page and home page.

**Employer (new)**
- **Hiring analytics dashboard** (`/analytics.html`, Chart.js): funnel applied -> reviewed ->
  shortlisted -> interviewed -> hired, average time to hire and to first response, applications per
  week, top applicant skills (with verified counts), most common missing skills, match distribution,
  per-job table and CSV export. Filter by job and period.
- Company rating summary on the employer dashboard.

**Admin (new)**
- **Payments tab:** revenue totals, revenue per month, recent transactions.
- **Reviews tab:** moderate company reviews (approve / reject with a note), with a pending badge.
- **Question bank tab:** create/edit/delete skill-test MCQs and mock-interview questions.
- **Audit log tab** (Spring AOP): who did what and when - logins and failed logins, user ban/unban,
  job create/close/reopen/delete, application status changes, skill/course CRUD, endorsements, review
  moderation and payments - with filters by action, user, result and date. Entries expire after 365
  days (TTL index). A `@Audited` annotation + `AuditAspect` records them; auditing never breaks the
  action itself.

**Account (new)**
- **Forgot password** with a 6-digit emailed code (15 min expiry, generic response so it never reveals
  which emails exist) and **change password** in Account settings. Changing or resetting the password
  invalidates every JWT issued before it (`User.passwordChangedAt`), so other devices are signed out.

### A note on skill endorsements
Endorsing a skill is allowed for **either** an admin or a peer student
(`PUT /api/students/{studentUserId}/skills/{skillId}/endorsement`, open to any logged-in student or
admin; a student cannot endorse their own skill). The admin page ships a full UI for this. Peer-to-peer
endorsement is fully implemented and reachable the same way at the API level (see Swagger), but does
not yet have its own frontend entry point (there is no student directory/search page to drive it from)
- a deliberate scope decision, not a bug, documented here for transparency.

**Spring Boot features demonstrated**
- Layered architecture: Controller → Service → Repository, with DTOs as Java records
- Spring Data MongoDB: derived queries, `@Query`, `MongoTemplate`, aggregation pipelines, indexes and a compound unique index
- Spring Security: stateless JWT filter, `@PreAuthorize` role checks, JSON 401/403 responses
- Bean Validation (`@Valid`, `@NotBlank`, `@Min`, ...) and a global exception handler (`@RestControllerAdvice`)
- A `@Scheduled` job that closes jobs automatically after their deadline
- `@Cacheable` / `@CacheEvict` for the skill list
- Email sending with `JavaMailSender` (signup OTP, status changes, deadline reminders, interviews);
  multipart file upload with validation, generated filenames and an authorization check before
  download, used for both the CV (private) and the employer logo (public)
- Profiles (`dev` / `prod`), environment variables, Actuator health check, Swagger
- Request logging filter, CORS configuration, startup data seeder, custom startup failure analyzers
- A second `@Scheduled` job (`DeadlineReminderScheduler`) for the bookmarked-job deadline reminders,
  following the same cron + `ApplicationReadyEvent` pattern as the existing job-closing scheduler
- A third-party payment gateway integration (SSLCommerz) via a plain `RestTemplate` bean: a session
  API call that returns a redirect URL, and a server-to-server validation call that is the only thing
  ever trusted to mark a payment as paid - never the browser redirect by itself

---

## Project Structure

```
src/main/java/com/skill2job
├── config        SecurityConfig, CorsConfig, OpenApiConfig, RequestLoggingFilter, DataSeeder, failure analyzers
├── controller    REST endpoints
├── dto           Request and response records
├── entity        MongoDB documents (User, StudentProfile, Job, Course, SavedJob, Interview,
│                 JobTemplate, ChatMessage, CourseProgress (embedded), Payment, ...)
├── enums         Role, JobType, JobStatus (incl. PENDING_PAYMENT), ApplicationStatus, InterviewStatus,
│                 CourseProgressStatus, PaymentPurpose, PaymentStatus, SubscriptionPlan
├── exception     ApiException, ErrorResponse, GlobalExceptionHandler
├── matching      MatchCalculator (the core algorithm), MatchResult, SkillGap
├── repository    Spring Data repositories
├── scheduler     JobDeadlineScheduler, DeadlineReminderScheduler
├── security      JwtUtil, JwtAuthenticationFilter, AuthUser, RestAuthHandlers
├── service       Business logic, including EmailService/OtpService (signup OTP + the newer status/
│                 deadline/interview emails), FileStorageService/CvService (CV upload and authorized
│                 download), ImageStorageService (employer logo upload, same security pattern as the
│                 CV), SavedJobService, InterviewService, ChatService, JobTemplateService,
│                 PaymentService/SslCommerzClient (job-posting fee + Pro subscription payments)
└── util          SkillNormalizer
src/main/resources/static   Frontend (HTML, CSS, JS)
src/test/java               Unit tests
```

---

## Running locally

### Requirements
- **JDK 25** (or JDK 21; then set `<java.version>21</java.version>` in `pom.xml`)
- **Maven 3.9+** (IntelliJ IDEA includes Maven)
- **MongoDB.** The app will not start without it. Choose ONE option:

#### Option A: MongoDB Atlas (easiest, nothing to install)
1. Create a free cluster at https://www.mongodb.com/cloud/atlas (see DEPLOY.md, step 1)
2. In **Network Access**, add your current IP address (or `0.0.0.0/0`)
3. Copy `.env.example` to a new file named **`.env`** in the project root (the folder that contains `pom.xml`)
4. Put your connection string in it:
   ```
   MONGODB_URI=mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/skill2job?retryWrites=true&w=majority
   ```
The app reads `.env` automatically when it runs from the project folder. The file is git-ignored, so your password is never pushed to GitHub.

#### Option B: Local MongoDB
1. Install **MongoDB Community Server** from https://www.mongodb.com/try/download/community
2. On Windows, keep "Install MongoDB as a Service" checked during setup
3. Make sure it is running: open `services.msc` and check that the **MongoDB** service is "Running"
4. No configuration needed. The app uses `mongodb://localhost:27017/skill2job` by default

#### Option C: Docker
If Docker Desktop is installed, run `docker compose up --build`. This starts MongoDB and the app together.

### Run from IntelliJ IDEA
1. Open the `skill2job` folder (File → Open) and let Maven import the dependencies
2. File → Project Structure → SDK: choose JDK 25
3. Run `Skill2JobApplication` (green arrow next to `main`)
4. If you use Atlas and prefer not to create a `.env` file: Run → Edit Configurations → Environment variables → add `MONGODB_URI=...`

### Run from the command line
```bash
mvn test              # run the unit tests
mvn spring-boot:run   # start the app
```

If MongoDB cannot be reached, the app stops with a short message explaining what to do, instead of a long stack trace.

### Open in the browser
- App: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Demo accounts (created automatically on the first run)
| Role | Email | Password |
|---|---|---|
| Student (backend) | student1@demo.com | Demo@123 |
| Student (frontend) | student2@demo.com | Demo@123 |
| Student (data) | student3@demo.com | Demo@123 |
| Student (backend, strong match) | student4@demo.com | Demo@123 |
| Student (early career, weak match) | student5@demo.com | Demo@123 |
| Employer | employer1@demo.com | Demo@123 |
| Employer | employer2@demo.com | Demo@123 |
| Admin | admin@skill2job.com | Admin@123 |

The seeded demo data already shows every new feature in a visible state: student1 has portfolio
links, two courses in progress/completed, an admin- and a peer-endorsed skill, a bookmarked job whose
deadline is only 2 days away (triggering an immediate deadline reminder), a shortlisted application
with a scheduled interview and an open chat thread with employer1. student3's application is
shortlisted with a *proposed but not yet picked* interview slot, so both interview states are visible.
student4 is left in a plain "Applied" state on purpose, ready for employer1 to try the bulk
shortlist/reject action on. Both employers have an "About us" page and a saved job template.
employer2 already has an active Pro subscription seeded (so none of its demo jobs needed a posting
fee), and employer1 has one completed demo job-posting payment, so the new Billing panel has example
content immediately.

Demo accounts are created directly (`AuthService.createUser`), so they are pre-verified and can log in
immediately - they never go through the OTP flow described below. Sign up a *new* account yourself to
see email verification in action.

Demo data is inserted only once, when the database has no skills. To start fresh, drop the `skill2job` database in MongoDB and restart the app.

---

## Environment Variables

| Variable | Purpose | Default (local only) |
|---|---|---|
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/skill2job` |
| `JWT_SECRET` | Secret used to sign tokens (at least 32 characters) | development secret |
| `JWT_EXPIRATION` | Token lifetime in milliseconds | 86400000 (1 day) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | First admin account | admin@skill2job.com / Admin@123 |
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` | dev |
| `SEED_ENABLED` | Insert demo data | true |
| `CORS_ORIGINS` | Other origins allowed to call the API | localhost |
| `PORT` | Server port (Render sets this) | 8080 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP login used to send the signup OTP. For Gmail, `MAIL_PASSWORD` must be a 16-character **app password**, not your normal login password | not set (see below) |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server | smtp.gmail.com / 587 |
| `MAIL_FROM` | "From" address on every email (with Brevo: a verified sender) | `MAIL_USERNAME` |
| `EMAIL_VERIFICATION_ENABLED` | Require the emailed 6-digit code at signup. Off: accounts are active immediately | `false` |
| `BREVO_API_KEY` | Send email through Brevo's HTTPS API instead of SMTP (needed on Render's free plan, which blocks SMTP ports) | not set (SMTP is used) |
| `OTP_EXPIRY_MINUTES` | How long a code stays valid | 5 |
| `OTP_RESEND_COOLDOWN_SECONDS` | Minimum wait between "Resend OTP" clicks | 60 |
| `OTP_MAX_ATTEMPTS` | Wrong codes allowed before a resend is required | 5 |
| `OTP_LOG_TO_CONSOLE` | Also print the OTP to the server log (handy without real SMTP) | true in `dev`, false in `prod` |
| `CV_UPLOAD_DIR` | Folder where uploaded CVs are stored | `./uploads/cv` |
| `CV_MAX_SIZE_MB` | Maximum CV file size | 5 |
| `LOGO_UPLOAD_DIR` | Folder where uploaded employer logos are stored | `./uploads/logos` |
| `LOGO_MAX_SIZE_MB` | Maximum logo file size | 2 |
| `APP_BASE_URL` | This app's own public URL, used to build the SSLCommerz success/fail/cancel/ipn callback URLs | `http://localhost:8080` |
| `SSLCOMMERZ_STORE_ID` / `SSLCOMMERZ_STORE_PASSWORD` | SSLCommerz sandbox credentials (see "Payments" below) | `testbox` / `qwerty` |
| `SSLCOMMERZ_SANDBOX` | Use SSLCommerz's sandbox endpoint instead of the live one | true |
| `FREE_JOB_POSTS` | Lifetime free job posts per employer before a posting fee applies | 2 |
| `JOB_POST_FEE` | One-time job-posting fee in BDT | 500 |
| `SUBSCRIPTION_PRICE` / `SUBSCRIPTION_DAYS` | Employer Pro subscription price (BDT) and length (days) | 2000 / 30 |
| `STUDENT_PRO_PRICE` / `STUDENT_PRO_DAYS` | Student Pro price (BDT) and length (days) | 299 / 30 |

All of these can be set as real environment variables or in the local `.env` file. **Always change `JWT_SECRET` and `ADMIN_PASSWORD` in production.**

If `MAIL_USERNAME`/`MAIL_PASSWORD` are left blank, no real email is sent; the app still works end to
end because the OTP is printed to the server log (only while `OTP_LOG_TO_CONSOLE=true`, which is the
default outside the `prod` profile) and "Resend OTP" always works.

---

## Email Verification (Signup OTP)

1. `POST /api/auth/register` creates the account immediately but **inactive** (`emailVerified=false`)
   and emails a 6-digit code (hashed with BCrypt before being stored - never saved as plain text, in
   its own `email_otps` collection, not on the `User` document).
2. The frontend sends the student/employer to `/verify-otp.html`, which calls
   `POST /api/auth/verify-otp` with the code. Success activates the account and returns a JWT, exactly
   like `/login` does.
3. A code expires after `OTP_EXPIRY_MINUTES` minutes, is rejected once already used, and is rejected
   after `OTP_MAX_ATTEMPTS` wrong tries - all three cases just tell the user to request a new one.
4. `POST /api/auth/resend-otp` issues a fresh code, replacing the old one, but refuses (`429`) if the
   previous code was sent less than `OTP_RESEND_COOLDOWN_SECONDS` ago.
5. `/login` itself rejects an unverified account with a 403 and a link back to the verification page.

Sending email is always best-effort: a missing or failing SMTP server never leaves an account
permanently stuck, since the code is still stored and can always be resent (and, in `dev`, is also
logged to the console).

---

## Matching Algorithm

For each required skill:
```
skillScore = min(studentLevel / requiredLevel, 1)      (0 if the student lacks the skill)
Match %    = Σ(skillScore × weight) / Σ(weight) × 100
```
- If a **required (mandatory)** skill is completely missing, `eligible = false` (the match percentage is still shown)
- Gap = requiredLevel − studentLevel; Priority = weight × gap, which decides what to learn first
- Courses: free courses first, then the course whose level is closest to the student's next level

**Example** (Junior Java Backend Developer):

| Skill | Required | Weight | Student | Score × Weight |
|---|---|---|---|---|
| Java (required) | 4 | 5 | 4 | 1 × 5 = 5 |
| Spring Boot (required) | 3 | 5 | 3 | 1 × 5 = 5 |
| SQL | 3 | 3 | 3 | 1 × 3 = 3 |
| Git | 2 | 2 | 3 | 1 × 2 = 2 |
| Docker | 2 | 2 | 0 | 0 |

Match = 15 / 17 × 100 = **88.2%**, eligible (Docker is not required). This calculation is covered by `MatchCalculatorTest`.

---

## Main API

| Method | Endpoint | Who |
|---|---|---|
| POST | `/api/auth/register` (multipart: `data` JSON part + optional `cv` file part) | Everyone |
| POST | `/api/auth/verify-otp`, `/api/auth/resend-otp`, `/api/auth/login` | Everyone |
| GET | `/api/auth/me` | Logged-in users |
| GET | `/api/cv/me` | Student |
| POST | `/api/cv/me` (multipart: `cv` file part) | Student (upload or replace) |
| DELETE | `/api/cv/me` | Student |
| GET | `/api/cv/{studentUserId}` | That student / Employer who received their application / Admin |
| GET | `/api/jobs?keyword=&type=&location=&skillId=&sort=&page=&size=` | Everyone |
| GET | `/api/jobs/{id}` | Everyone |
| POST / PUT | `/api/jobs`, `/api/jobs/{id}` | Employer |
| PATCH | `/api/jobs/{id}/close`, `/api/jobs/{id}/reopen` | Employer |
| DELETE | `/api/jobs/{id}` | Employer (own jobs) / Admin |
| GET | `/api/jobs/{id}/match` | Student |
| POST | `/api/jobs/{id}/apply?force=false` | Student |
| GET | `/api/jobs/{id}/applications?minMatch=&department=&minCgpa=` | Employer (own jobs) / Admin |
| GET | `/api/jobs/compare?ids=id1,id2,...` | Everyone (up to 4 jobs) |
| PATCH | `/api/applications/{id}/status` | Employer / Admin |
| PATCH | `/api/applications/bulk-status` | Employer / Admin |
| GET / PUT | `/api/student/profile` | Student |
| POST / DELETE | `/api/student/skills`, `/api/student/skills/{skillId}` | Student |
| PUT / DELETE | `/api/student/course-progress`, `/api/student/course-progress/{courseId}` | Student |
| GET / POST / DELETE | `/api/student/saved-jobs`, `/api/student/saved-jobs/{jobId}` | Student |
| PUT | `/api/students/{studentUserId}/skills/{skillId}/endorsement` | Student (not self) / Admin |
| GET | `/api/student/recommended-jobs`, `/applications`, `/dashboard` | Student |
| GET / PUT | `/api/employer/profile`; GET `/api/employer/jobs`, `/dashboard` | Employer |
| POST | `/api/employer/profile/logo` (multipart: `logo` file part) | Employer |
| GET / POST / DELETE | `/api/employer/job-templates`, `/api/employer/job-templates/{id}` | Employer |
| POST | `/api/payments/job/{jobId}/initiate`, `/api/payments/subscription/initiate?plan=PRO` | Employer |
| GET | `/api/payments/me` (billing history) | Employer |
| POST | `/api/payments/sslcommerz/success`, `/fail`, `/cancel`, `/ipn` | SSLCommerz only (no login) |
| GET | `/api/companies/{employerUserId}`, `/api/companies/{employerUserId}/logo` | Everyone (public branding page) |
| GET / POST / PATCH | `/api/interviews`, `/api/interviews` (propose), `/api/interviews/{id}/select`, `/{id}/cancel` | Student / Employer / Admin |
| GET / POST | `/api/applications/{applicationId}/messages` | Student / Employer / Admin, once shortlisted or hired |
| GET | `/api/skills`, `/api/skills/search?q=` | Everyone |
| POST / PUT / DELETE | `/api/skills/**`, `/api/courses/**` | Admin |
| GET | `/api/courses` | Everyone |
| GET / PATCH | `/api/notifications`, `/unread-count`, `/{id}/read`, `/read-all` | Logged-in users |
| GET / PATCH | `/api/admin/users`, `/api/admin/users/{id}/active`, `/api/admin/dashboard` | Admin |
| GET | `/api/admin/students/{userId}/profile` | Admin (used by the skill-endorsement panel) |
| GET / POST | `/api/quizzes`, `/api/quizzes/{skillId}/start`, `/api/quizzes/attempts/{id}/submit`, `/api/quizzes/attempts` | Student |
| GET / POST | `/api/mock-interviews/skills`, `/api/mock-interviews`, `/api/mock-interviews/{id}`, `/{id}/submit` | Student |
| GET / POST / PUT / DELETE | `/api/student/job-alerts`, `/{id}`, `/{id}/preview`, `/run` | Student |
| GET | `/api/student/roadmap?jobId=&hoursPerWeek=` | Student |
| GET / PUT | `/api/student/cv` (CV builder data) | Student |
| GET | `/api/student/achievements`, `/api/leaderboard` | Student / logged-in users |
| GET / POST / DELETE | `/api/companies/{employerId}/reviews`, `/api/student/reviews/reviewable`, `/api/student/reviews/{id}` | Everyone (read) / Student (write) |
| GET | `/api/public/featured-reviews` | Everyone |
| GET | `/api/employer/analytics?jobId=&weeks=`, `/api/employer/reviews` | Employer |
| POST | `/api/auth/forgot-password`, `/api/auth/reset-password` | Everyone |
| POST | `/api/auth/change-password` | Logged-in users |
| GET / POST / PUT / DELETE | `/api/admin/quiz-questions`, `/api/admin/interview-questions` | Admin |
| GET / PATCH | `/api/admin/reviews`, `/api/admin/reviews/{id}`, `/api/admin/pending-counts` | Admin |
| POST / GET | `/api/payments/student/pro/initiate`, `/api/payments/student/premium` | Student |
| GET | `/api/payments/me` | Employer / Student (own payments only) |
| GET | `/api/admin/audit-logs`, `/api/admin/audit-logs/actions`, `/api/admin/payments/summary` | Admin |

Every endpoint is documented in Swagger UI with "Try it out". Click **Authorize** and paste the token from the login response to call protected endpoints. For `/api/auth/register`, Swagger shows it as a multipart form: paste the JSON body into the `data` part and pick a file for the optional `cv` part.

---

## Payments (SSLCommerz sandbox)

Employers get `FREE_JOB_POSTS` (default 2) lifetime free job posts. Posting another job after that
creates it with status `PENDING_PAYMENT` - it is saved but never appears in a public search - and the
frontend immediately opens an SSLCommerz checkout for `JOB_POST_FEE` (default 500 BDT). The job flips
to `OPEN` the moment the payment is confirmed. Buying the **Pro** subscription
(`SUBSCRIPTION_PRICE`/`SUBSCRIPTION_DAYS`, default 2000 BDT / 30 days, from the employer dashboard's
Billing panel) waives the posting fee entirely while it is active; renewing early extends the current
expiry instead of restarting it.

**Setup.** The defaults (`SSLCOMMERZ_STORE_ID=testbox`, `SSLCOMMERZ_STORE_PASSWORD=qwerty`) are
SSLCommerz's own published sandbox demo credentials, so payments work out of the box with **no signup
required**. If they ever stop working, register a free personal sandbox store at
https://developer.sslcommerz.com/registration/ and set `SSLCOMMERZ_STORE_ID` /
`SSLCOMMERZ_STORE_PASSWORD` in your `.env` - nothing else changes. The sandbox checkout page accepts
test card numbers and test mobile-wallet (bKash/Nagad/Rocket) flows shown right on the page; no real
money ever moves.

**How it works end to end:** the backend opens a checkout session server-to-server and gets back a
`GatewayPageURL`; the frontend does a full-page redirect there. After paying (or cancelling), SSLCommerz
redirects the browser back to `/api/payments/sslcommerz/success|fail|cancel`, which re-confirms the
payment with SSLCommerz's own Order Validation API before applying any effect - the browser redirect
is never trusted by itself - then redirects to `payment-result.html` with a friendly message.
SSLCommerz may also call `/api/payments/sslcommerz/ipn` server-to-server as a reliability backup; this
only works when `APP_BASE_URL` is a real public URL (it is simply never reached on `localhost`), and
either path applying the same payment twice is handled safely (idempotent).

### Student Pro (student payments)

Students can buy **Student Pro** (`STUDENT_PRO_PRICE` / `STUDENT_PRO_DAYS`, default 299 BDT / 30 days) on
`/premium.html` (Career tools -> Student Pro). It uses exactly the same SSLCommerz session, callback and
Order Validation code as the employer payments; only the owner and the effect differ:

- **Linked to the right account:** the payment stores `studentId` + `payerRole=STUDENT`. The client never
  sends an id - it comes from the login token - so a student can only pay for, and be credited to, themselves.
- **Activated only after verification:** `StudentProfile.proExpiresAt` is written only by
  `PaymentService`, and only after the Validation API confirmed the same `tran_id`, amount and currency.
  Renewing while active extends the current expiry. A failed or cancelled payment grants nothing.
- **What Pro gives (enforced on the server):** no cooldown between skill-test retakes
  (`QuizRules.retakeAt`) and 30 job alerts instead of 10 (`JobAlertService.maxAlerts`).
- **Roles cannot be mixed:** each `PaymentPurpose` belongs to exactly one role (`payerRole()`);
  `POST /api/payments/student/pro/initiate` is STUDENT-only, the employer endpoints are EMPLOYER-only, and
  `PaymentRules.inconsistency()` refuses to confirm or apply a payment whose purpose, role and owner id
  disagree. `GET /api/payments/me` returns only the caller's own role's history.
- **Duplicate / concurrent callbacks:** the browser redirect and the IPN can arrive at the same time, so the
  `PENDING/FAILED/CANCELLED -> SUCCESS` change is one atomic conditional update and the effect is applied
  by whoever atomically flips `effectPending` true -> false. A late fail/cancel callback can never undo a
  SUCCESS. If applying the effect crashes, the flag is restored and the next callback retries it.
- **Admin:** the Payments tab shows Student Pro revenue, active Student Pro count, and the payer's name and role.

A billing panel on the employer dashboard shows the current plan, an "Upgrade to Pro" / "Renew Pro"
button, and a payment-history table (`GET /api/payments/me`). A job awaiting payment shows a **Pay
now** button in "My jobs" instead of the usual Close/Reopen toggle.

## Upgrading an existing database

The new question banks (`quiz_questions`, `interview_questions`) and the feature demo data (reviews,
XP/streaks, verified skills, a job alert, CV sections for the demo student) are seeded on the next
start even when the database already has data: each seeder checks only its own collection. Nothing
existing is changed or deleted.

## Limitations
- Matching is rule-based, not AI
- Students enter their own skill levels; only skills that pass a skill test are marked as verified
- Mock-interview feedback is keyword-based, not AI; it checks coverage of key points, not correctness of reasoning
- Demo company reviews, XP and verified skills created by the seeder are fictional demo data
- Uploaded CVs (PDF/DOC/DOCX) are stored and downloadable, but their content is not parsed
- CVs and employer logos are stored on local disk (`CV_UPLOAD_DIR`, `LOGO_UPLOAD_DIR`); on Render's
  free plan this disk is **not persistent** across deploys/restarts, so uploaded files can be lost -
  fine for a demo, not for production
- Courses are added manually by the admin. The seed data links to official or free learning sites; open each link once before submitting
- Demo companies and jobs are fictional
- If a signup is abandoned before the OTP is verified, the account and any uploaded CV are left behind
  unverified rather than cleaned up automatically
- Skill endorsement is open to both admins and peer students at the API level, but only the admin
  side has a dedicated UI panel; see "A note on skill endorsements" above
- Interview time slots are freeform strings proposed by the employer (no calendar integration) and
  the in-app chat has no file attachments or real-time push - the page polls on load/send
- Email sending (OTP, status changes, deadline reminders, interview notices) is always best-effort:
  a missing or misconfigured SMTP server never blocks the action that triggered it, and the action
  still succeeds with the result staying purely in-app
- Student Pro is a time-limited plan with two benefits (retake cooldown, alert cap); it is not a content paywall
- Payments run against SSLCommerz's **sandbox** only (no real money ever moves); the IPN callback
  needs a publicly reachable `APP_BASE_URL` and is simply never called on `localhost`, though the
  ordinary success/fail/cancel redirect flow works fine locally without it

See also: [DEPLOY.md](DEPLOY.md) (deploying to Render), [DESIGN.md](DESIGN.md) (database design) and [VIVA.md](VIVA.md) (viva questions and answers).
