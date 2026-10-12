# Viva Preparation: Likely Questions and Answers

## Project and idea

**1. What is the project, in one line?**
A job portal that compares a student's skills with a job's requirements, calculates a match percentage, shows which skills are missing or weak, and suggests courses to learn them.

**2. How is it different from a normal job portal?**
A normal portal only lists jobs. This one tells the student, for example, "to get this job you need to learn Docker, and here is a free course to start with."

**3. Do similar platforms already exist?**
Yes, for example JobCannon, Pluralsight Skills and LinkedIn Learning's skill-based suggestions. Ours is smaller, rule-based and focused on fresh graduates in Bangladesh, with job posting, applications and gap analysis in one system.

**4. Is this AI?**
No. It is weighted, rule-based matching. Every result comes from a fixed formula, so every result can be explained.

## Matching algorithm

**5. How is the match percentage calculated?**
For each skill, score = min(student level ÷ required level, 1). Then Σ(score × weight) ÷ Σ(weight) × 100.

**6. Why min(…, 1)?**
Without the cap, being far above the requirement in one skill could hide a gap in another skill. So each skill contributes at most a score of 1.

**7. Why weights?**
Not all skills matter equally. For a Java backend job, Java (weight 5) matters more than Git (weight 2).

**8. What happens if a mandatory skill is missing?**
`eligible = false`, but the match percentage is still shown. The student can still apply with "Apply anyway", and the employer sees "Eligible: No" in the applicant list.

**9. Work through an example.**
Java 4/4 (w5) = 5, Spring 3/3 (w5) = 5, SQL 3/3 (w3) = 3, Git 3/2 → 1 × 2 = 2, Docker 0/2 (w2) = 0. Total 15/17 = 88.2%.

**10. How are courses ordered?**
First the skills are ordered by priority (weight × gap). For each skill, free courses come first, then the course whose level is closest to the student's next level.

**11. How are "ReactJS" and "React" treated as the same skill?**
`SkillNormalizer` lowercases the text and removes punctuation ("React.js" → "reactjs"), then compares it with each skill's name and alias list.

## Spring Boot

**12. What is the architecture?**
Layered: Controller (HTTP) → Service (business logic) → Repository (database). Entities are never returned directly; DTOs (Java records) are returned instead.

**13. Why use DTOs?**
So that data such as passwords never appears in a response, and so the API shape can differ from the database shape.

**14. Where is dependency injection used?**
Constructor injection in every service and controller. Spring creates the objects, which makes testing and changes easier.

**15. How does validation work?**
Annotations such as `@NotBlank`, `@Email`, `@Min` and `@Max` on the DTOs, and `@Valid` in the controllers. Invalid input returns 400 with per-field messages from `GlobalExceptionHandler`. Business rules (deadline, duplicate application) are checked in the service layer.

**16. How are exceptions handled?**
`@RestControllerAdvice` catches every exception and returns JSON with the same shape. The custom `ApiException` carries the HTTP status (404, 409, ...).

**17. What does `@Scheduled` do?**
Every day at 00:05 (Asia/Dhaka) it closes jobs whose deadline has passed and notifies the employer. It also runs once at startup.

**18. Where is caching used?**
The skill list is needed on almost every page, so it uses `@Cacheable("skills")`. Adding, updating or deleting a skill clears the cache with `@CacheEvict`.

**19. Why profiles (dev/prod)?**
Locally the app shows debug logs and demo credentials; in production it logs less and hides credentials. All secrets come from environment variables.

## MongoDB

**20. Why MongoDB instead of MySQL?**
A job's required skills and a student's skills are naturally lists, so they can be embedded inside the document. One read gives the matcher all the data with no joins. Free hosting on Atlas is also easy.

**21. What did you embed and what did you reference?**
Skills (of students and of jobs) are embedded because they are always used together and are few. Applications are a separate collection because they can grow large and are queried from two sides. (See DESIGN.md.)

**22. Where is aggregation used?**
In the admin dashboard: open jobs → `$unwind requiredSkills` → `$group` by skill name → count → sort → limit 10. This finds the most demanded skills.

**23. How do you prevent duplicate applications?**
At two levels: the service checks first, and the database has a compound unique index on `{jobId, studentId}`. Even with two simultaneous clicks, the second insert fails with `DuplicateKeyException`, which is returned as 409.

**24. How are the dynamic filters built?**
With `MongoTemplate` and `Criteria`, adding only the filters that were provided. The keyword is escaped with `Pattern.quote`, so a user cannot break the query by sending a regular expression.

## Security

**25. How does JWT work here?**
On login, the server returns a signed token containing the user id, role and expiry time. The browser sends it in the `Authorization: Bearer` header with every request. A filter verifies the signature and expiry and puts the user into the SecurityContext. The server keeps no session (stateless).

**26. How is role-based access implemented?**
With `@PreAuthorize("hasRole('EMPLOYER')")` and similar. A role alone is not enough: the service layer also checks ownership so an employer cannot edit another employer's job.

**27. How are passwords stored?**
As salted BCrypt hashes. Even if the database is stolen, the original passwords cannot be recovered.

**28. Why is CSRF protection disabled?**
CSRF attacks target cookie-based logins. The token is sent in a header, which the browser never sends automatically, so the risk does not apply.

**29. How is XSS prevented?**
The frontend escapes all user-provided text before inserting it into HTML (`SB.esc`). Course links are only opened if they start with `http(s)`.

## Email verification and CV upload

**30. Walk through what happens when someone signs up.**
`register()` creates the `User` straight away but with `emailVerified=false`, so it cannot log in yet. A 6-digit code is generated, BCrypt-hashed, and saved in its own `email_otps` document (one per user, keyed by `userId`), then emailed. The frontend moves to a "verify your email" page; `POST /api/auth/verify-otp` checks the code against the hash, and only on success flips `emailVerified` to `true` and returns a JWT, exactly like login.

**31. Why store the OTP in a separate collection instead of fields on `User`?**
It has a completely different lifecycle — it expires in minutes, gets overwritten on every resend, and becomes irrelevant forever once verified. Keeping it separate (referenced by `userId`) keeps `User` focused on the account itself, the same reasoning used for `applications` being its own collection instead of embedded in `users`.

**32. Why hash the OTP instead of storing it as plain text?**
Same reason as passwords: if the database were read by someone else, they still could not read out a valid code. It reuses the project's existing BCrypt `PasswordEncoder` bean rather than adding a second hashing scheme.

**33. What stops someone from brute-forcing the 6-digit code?**
Three independent limits: it expires after `OTP_EXPIRY_MINUTES` (default 5), it is rejected after `OTP_MAX_ATTEMPTS` wrong tries (default 5) and must then be resent, and it is single-use (marked `used` the moment it succeeds).

**34. What happens if the mail server is down or not configured?**
Sending is best-effort: `EmailService` catches the failure, logs it, and returns `false` rather than throwing. The account and its OTP document already exist, so the user can still get a working code through "Resend OTP"; in the `dev` profile the code is also printed to the server log (`app.otp.log-to-console`) so the whole flow can be demoed without real SMTP credentials.

**35. How is the CV stored, and what stops path traversal?**
The file is written to disk under `CV_UPLOAD_DIR` using a freshly generated UUID filename — the user's original filename (and anything in it, like `../../etc/passwd`) is never used to build a path. Only the extension (checked against an allow-list of pdf/doc/docx) and the original name for display purposes are taken from it. Looking a file back up re-validates that the stored name contains no `/`, `\` or `..` before resolving it, as a second layer of defence.

**36. Who is allowed to download a student's CV?**
`CvService` allows exactly three cases: the student themselves (`/api/cv/me`), any admin, or an employer who has an actual application from that student (checked against the `applications` collection) — never employers in general and never other students.

## New features (portfolio, endorsements, saved jobs, interviews, chat, templates, analytics)

**38. How does skill endorsement work, and who can do it?**
`StudentSkill` has `endorsed`, `endorsedByName` and `endorsedAt` fields. `PUT
/api/students/{studentUserId}/skills/{skillId}/endorsement` is open to any logged-in student (except
endorsing their own skill, which `StudentService.setEndorsement` explicitly rejects) or an admin. The
admin page has a full panel for it; peer endorsement works the same way at the API level but has no
dedicated frontend page yet - a documented scope decision, not a missing feature.

**39. How are bookmarked jobs different from an application?**
A `SavedJob` is just a bookmark - no skill check, no eligibility, nothing sent to the employer. It is
its own collection with a compound unique index on `{studentId, jobId}` so the same job cannot be
bookmarked twice, and it is deleted automatically if the job itself is deleted.

**40. How do deadline reminders get sent without the student asking for them?**
`DeadlineReminderScheduler` runs once a day (and once at startup, so the demo is visible immediately)
over every `SavedJob` that has not been reminded yet. If the bookmarked job's deadline is 0-2 days
away, it sends an in-app notification and a best-effort email, then flips `deadlineReminderSent` so it
is never sent twice for the same bookmark.

**41. How does interview scheduling work?**
The employer calls `POST /api/interviews` with a list of proposed time-slot strings, creating an
`Interview` with status `PROPOSED`. The student calls `PATCH /api/interviews/{id}/select` with one of
those exact strings, which moves it to `SCHEDULED` and records `selectedSlot`. Either side can see
their interviews with `GET /api/interviews` (students see their own, employers see the ones they
proposed); the employer or an admin can cancel one at any point.

**42. When does the in-app chat open, and why gate it?**
Only once an application is `SHORTLISTED` or `HIRED` (`ChatService` checks `ApplicationStatus` on both
`list()` and `send()`). That matches the feature as specified - "chat between employer and shortlisted
applicant" - and avoids every applicant being able to message every employer the moment they apply.

**43. What is a job template, and how is it different from just copying an old job post?**
A `JobTemplate` stores a name, title, description, type, location and required-skills list, completely
separate from any actual `Job` document - editing or deleting a past job post never touches a template
made from it. `post-job.html?template={id}` prefills the "New job" form from one, but the deadline is
always left for the employer to set fresh (a template has no deadline of its own).

**44. How does applicant filtering by match %, department and CGPA work under the hood?**
`ApplicationService.forJob` builds a `MongoTemplate` `Criteria` with only the filters that were
actually supplied - `matchPercent.gte(minMatch)`, a case-insensitive exact-match regex on `department`,
and `cgpa.gte(minCgpa)` - the same "add only what's provided" pattern already used for the public job
search filters.

**45. Where do `department` and `cgpa` on an application come from, since a student's profile can change later?**
They are snapshotted onto the `JobApplication` at the moment of `apply()`, from the student's profile
at that instant. That is deliberate: an employer filtering applicants should see what was true when the
student applied, not a value that silently changed afterwards because the student edited their profile.

**46. What is bulk shortlist/reject, and what happens if one of the selected applications cannot be moved?**
`PATCH /api/applications/bulk-status` takes a list of application ids and one target status.
`ApplicationService.bulkUpdateStatus` applies the existing single-application rules
(`updateStatus`) to each one inside a loop, catching `ApiException` per item - an application that is
not owned by this employer, or whose current status cannot move to the requested one, is skipped (with
its reason) rather than failing the whole batch.

**47. What counts as "hiring-funnel analytics", and how is time-to-hire calculated?**
`DashboardService.hiringFunnel` counts applications by status (Applied/Shortlisted/Hired/Rejected) for
an employer's jobs and derives two conversion rates: Applied→Shortlisted and Shortlisted→Hired. Average
time-to-hire is `ChronoUnit.DAYS.between(appliedAt, updatedAt)` averaged over every `HIRED` application
- `updatedAt` is the timestamp of the last status change, which for a hired application is the moment
it became `HIRED`.

**48. Why does the employer logo use a separate `ImageStorageService` instead of reusing `FileStorageService`?**
They follow the exact same security pattern (UUID filenames, an allow-list, strict path-traversal
checks on lookup, a max-size limit), but they are not interchangeable: the CV is private (only the
owning student, an admin, or an employer who received an application may download it) while the logo
is intentionally public (`SecurityConfig` permits `GET /api/companies/**` with no auth at all), and
they validate a different allow-list (PDF/DOC/DOCX vs. PNG/JPG/WEBP). Keeping them as two small,
single-purpose services was simpler and safer than branching one service on a "is this public" flag.

**49. Beyond the signup OTP, what other emails does the app send, and how do they stay reliable?**
Application-status changes, deadline reminders for bookmarked jobs, and interview-proposed/-scheduled
notices. All of them go through the same private `send(to, subject, body)` helper in `EmailService`
that the OTP already used, which catches any `MailException`, logs it, and returns rather than
throwing - so a down or misconfigured SMTP server never blocks the status change, reminder, or
interview action that triggered the email; the in-app notification (which does not depend on SMTP)
still goes out either way.

## Payments (job-posting fee + Pro subscription)

**50. Why did you add payments, and what exactly costs money?**
Two things: posting a job past a free quota (`FREE_JOB_POSTS`, default 2 per employer, lifetime), and
an optional Pro subscription that removes that fee entirely while active. It demonstrates a real
third-party payment-gateway integration, not just another CRUD feature - session creation, a hosted
checkout redirect, and server-to-server callback verification.

**51. Walk through what happens when an employer posts a job that needs payment.**
`JobService.create()` asks `PaymentService.jobPostingRequiresPayment()`: if the employer has no active
Pro subscription and has already used their free quota (`JobRepository.countByEmployerId()`), the job
is saved with `status=PENDING_PAYMENT` instead of `OPEN` - so it is never returned by the public job
search (which always filters `status=OPEN`), but nothing is lost if the employer never pays. The
frontend then calls `POST /api/payments/job/{id}/initiate`, which opens an SSLCommerz checkout session
server-to-server and redirects the browser to the returned `GatewayPageURL`.

**52. The browser redirects back and says the payment succeeded. Why isn't that trusted directly?**
Because a browser redirect is just a URL the customer's browser was sent to - it could be replayed,
bookmarked, or forged by anyone who guesses the shape. `PaymentService.processCallback` only ever
marks a payment `SUCCESS` after calling SSLCommerz's own **Order Validation API** server-to-server
with the `val_id` from the callback and getting `VALID`/`VALIDATED` back. Only then does it open the
job or extend the subscription. This is the same principle as never trusting a client-supplied price
in an e-commerce checkout.

**53. SSLCommerz can call both a redirect and a separate IPN endpoint for the same payment. How do you avoid charging the effect twice?**
`processCallback` checks `payment.getStatus() == SUCCESS` first and returns the same redirect
immediately if it is already applied - so whichever callback (the browser's success_url, or
SSLCommerz's server-to-server `ipn_url`) arrives second is a no-op. The IPN URL also simply never gets
called at all when the app runs on `localhost` (it needs a public URL), so the whole flow is designed
to work correctly with or without it ever firing.

## Advanced features (skill tests, alerts, roadmap, CV builder, gamification, reviews, audit log)

**54. How do you stop a student from cheating on a skill test?**
The server chooses the questions when the attempt starts and stores their ids on the `QuizAttempt`. The
browser only receives question text and options - never `correctIndex`. Scoring happens on submit
against the stored ids, there is a time limit checked on the server, and starting again returns the same
unfinished attempt instead of new questions. After finishing, a cooldown stops instant retakes.

**55. How does a quiz score become a verified level?**
`QuizRules.levelFor`: 90%+ -> level 5, 75%+ -> 4, 60%+ -> 3, 40%+ -> 2. 60% is the pass mark, so only
levels 3-5 verify a skill. The result is written to the embedded `StudentSkill` as `verified`,
`verifiedLevel` and `verifiedScore`.

**56. How do job alerts work without sending the same job twice?**
A `@Scheduled(cron = "${app.alerts.cron}")` job runs every 15 minutes. For each active alert it finds
open jobs published after the alert was created that match its filters (and the minimum match %
computed with the same `MatchCalculator`), but only those published in the window since the alert's
last check (`lastCheckedAt`, upper bound = the time this check started). It then sends one notification
and an optional email digest and moves `lastCheckedAt` forward, so a job is never sent twice.

**57. How is the learning roadmap generated?**
`RoadmapService` gets the gaps from the matcher (for one job, or the most demanded skills of all open
jobs). `RoadmapPlanner` (pure, unit-tested) picks up to 3 courses per gap in increasing level order,
skips completed ones, estimates hours as 6 + 4 x course level, and packs the steps into weeks using the
student's hours per week, adding a skill test after each skill. It also returns the projected match %.

**58. How does the mock-interview feedback work if there is no AI?**
Each question has key points. `AnswerEvaluator` normalises and stems the words of the answer and of each
key point; a key point counts as covered when at least 60% of its keywords appear. The session score is
70% coverage + 30% self-rating. The evaluator is behind one method, so an AI reviewer could replace it.

**59. Why use Spring AOP for the audit log?**
Auditing is a cross-cutting concern. Instead of adding logging code to every service method, methods are
marked `@Audited(action = "JOB_DELETE", ...)` and `AuditAspect` records the entry after the method
returns or throws. Business code stays clean, and the aspect catches its own errors so auditing can
never break the real action. Login is audited explicitly because a failed login must be recorded
without the password.

**60. How are company reviews kept honest?**
Only students whose application to that company was responded to (shortlisted, rejected, hired) or who
were interviewed can review it; there is one review per student per company (compound unique index);
every new or edited review is PENDING until an admin approves it, and the admin action is audited.
Anonymous reviews hide the name on public pages but are still linked to a real account.

**61. What happens to old login tokens after a password change?**
`User.passwordChangedAt` is set. `JwtAuthenticationFilter` compares it (to the second) with the token's
`iat` and rejects any older token, so every other device is signed out. The change-password endpoint
returns a fresh token for the current device.

**62. How do you make forgot-password safe?**
The response is the same whether or not the email exists (no account enumeration), the 6-digit code is
stored hashed, expires in 15 minutes and allows only a few attempts, and a successful reset also
invalidates old tokens.

**63. What was the SSLCommerz bug that stopped payments from working?**
`restTemplate.postForObject(..., Map.class)` returns a raw `Map<?, ?>`. Calling
`getOrDefault("failedreason", "...")` on a wildcard map does not compile ("String cannot be converted to
capture#1 of ?"). The fix converts the response once into a typed `Map<String, Object>`. While fixing it
the validation step was also hardened: the validated `tran_id`, amount and currency must match the
stored payment before it is marked successful.

**64. How do students pay, and how do you make sure a student gets Pro only after a real payment?**
`POST /api/payments/student/pro/initiate` (STUDENT only) creates a PENDING `Payment` with the student's id
taken from the JWT and opens an SSLCommerz sandbox session. The browser is redirected to the gateway and
back to `/api/payments/sslcommerz/success`, but that redirect proves nothing. The backend calls the Order
Validation API with the `val_id` and only accepts it when the returned `tran_id`, amount and currency match
this exact payment (`PaymentRules.mismatch`). Only then is `StudentProfile.proExpiresAt` written - there is
no other code path that sets it.

**65. How do you keep employer and student payments from being confused?**
Each `PaymentPurpose` carries its role (`JOB_POST` and `SUBSCRIPTION` are EMPLOYER, `STUDENT_PRO` is
STUDENT). The initiate endpoints are role-guarded with `@PreAuthorize` and re-check the role in the service.
A student payment stores `studentId` and no `employerId` (and vice versa), and `PaymentRules.inconsistency`
refuses to confirm or apply a payment where purpose, role and owner id disagree. History (`/api/payments/me`)
is looked up by the caller's own role and id, and the admin table shows the payer's name and role.

**66. What if SSLCommerz calls the success URL and the IPN at the same time?**
Both can see the payment as PENDING, so "check then save" would apply the effect twice. Instead the change
to SUCCESS is one atomic Mongo conditional update, and the effect is applied only by the caller that
atomically flips `effectPending` from true to false. If applying it throws, the flag is put back and the
next callback retries. A late "failed" callback only updates payments that are still PENDING, so it can
never overwrite a paid one.

## Testing, deployment and limitations

**37. What did you test, and what are the limitations?**
Unit tests cover the matching formula (with hand-calculated values), the skill normalizer, application status transitions and JWT handling (valid, tampered, expired). Deployment uses a multi-stage Docker build on Render with MongoDB Atlas and the `/actuator/health` health check. Limitations: students enter their own skill levels, uploaded CVs are stored but not parsed, the course list is maintained manually, CVs and employer logos live on local disk so they do not survive a redeploy on Render's free plan, peer skill-endorsement has no dedicated UI yet (API-only), interview slots are freeform strings with no calendar integration, and payments run against SSLCommerz's sandbox only (no real money ever moves, by design for a student project).

---

## Suggested demo order
1. Landing page → "How the score works"
2. Sign up as a new student with a CV attached → show the "verify your email" page → if `OTP_LOG_TO_CONSOLE=true` (default locally), read the code from the server log; otherwise from the actual inbox → enter the code → land on the profile page logged in
3. Profile page → "CV / Resume" panel → download the CV just uploaded, to show it round-trips. Add one or two skills, then apply to "Junior Java Backend Developer" (demo employer1's job) so there is a real application to show an employer next
4. Log in as student1 (a seeded demo account, already has skills and an application) → dashboard (missing-skills chart)
5. Jobs page → ranked by match
6. "Junior Java Backend Developer" → gap report → Docker course → Apply
7. My skills page → type "reactjs" and show it is saved as React → change a level → go back to a job and show the match changed
8. Log in as employer1 → Applicants for "Junior Java Backend Developer" → filter by match %/department/CGPA → "Download CV" on the new student's row (step 3) to show an authorized download succeeding, then on student1's row to show the friendly "no CV uploaded" message for a seeded account → select student4's row and try the bulk shortlist action → open the chat thread already running with student1 (Rahim) and send a message → propose another interview slot → open "Hiring funnel" on the dashboard
9. Employer1 → Company profile: upload a logo, write an "About us" page, save a job template, then start "New job" from that template to show it prefilled
9b. Employer1 → Billing panel: already has one completed demo payment to show in the history table → post one more job past the free quota to trigger "Pay now" → the SSLCommerz sandbox checkout page (no real card needed; use its own test payment options) → redirected back to the friendly payment-result page and the job is now Open. Employer2 already has an active Pro subscription seeded, so its jobs never needed a fee - show its Billing panel for contrast
10. Log in as student1 → profile page: portfolio links, course-progress bar, the admin-endorsed Java badge → Saved jobs page (shows the deadline-reminder notification that fired at startup) → Interviews page (the already-scheduled slot) → Compare page (pick two jobs from the job list)
11. Admin → aggregation charts, add a skill with aliases, try to delete a skill that is in use and show it is blocked → Skill endorsements panel: endorse one of a student's skills
12. Swagger UI → call an endpoint with the token
