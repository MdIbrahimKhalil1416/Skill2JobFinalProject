package com.skill2job.config;

import com.skill2job.dto.ApplicationResponse;
import com.skill2job.dto.InterviewResponse;
import com.skill2job.dto.JobTemplateRequest;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.Course;
import com.skill2job.entity.CourseProgress;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.entity.Payment;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.entity.User;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.CourseProgressStatus;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.JobType;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import com.skill2job.enums.SubscriptionPlan;
import com.skill2job.repository.CourseRepository;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.PaymentRepository;
import com.skill2job.repository.SkillRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.security.AuthUser;
import com.skill2job.service.ApplicationService;
import com.skill2job.service.AuthService;
import com.skill2job.service.ChatService;
import com.skill2job.service.InterviewService;
import com.skill2job.service.JobService;
import com.skill2job.service.JobTemplateService;
import com.skill2job.service.SavedJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs once at startup.
 * 1. Makes sure an admin account exists (from ADMIN_EMAIL / ADMIN_PASSWORD) - see ProductionRules.adminDecision:
 *    an existing account is never touched, and production never creates an admin with a default/weak password.
 * 2. Only where ProductionRules.demoSeedingAllowed (app.seed.enabled, which is OFF by default and which the
 *    production profile additionally ignores unless app.seed.allow-in-prod=true) and there are no skills yet, inserts demo data covering every
 *    feature in the app (including the newer portfolio links, course progress, skill endorsements,
 *    saved jobs, interviews, chat, job templates, company branding and hiring-funnel analytics)
 *    so a fresh install already has something to look at.
 */
@Component
@DependsOn("productionConfigGuard")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Demo@123";

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final CourseRepository courseRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final PaymentRepository paymentRepository;
    private final AuthService authService;
    private final ApplicationService applicationService;
    private final SavedJobService savedJobService;
    private final InterviewService interviewService;
    private final ChatService chatService;
    private final JobTemplateService jobTemplateService;
    private final CacheManager cacheManager;
    private final QuestionBankSeeder questionBankSeeder;
    private final FeatureDemoSeeder featureDemoSeeder;
    private final Environment environment;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.admin.name}") private String adminName;
    @Value("${app.seed.enabled:false}") private boolean seedEnabled;
    @Value("${app.seed.allow-in-prod:false}") private boolean allowSeedInProd;
    /** Skill-test / interview question banks are product content, not sample records; they only load into an empty collection. */
    @Value("${app.seed.question-banks:true}") private boolean seedQuestionBanks;
    @Value("${app.seed.print-credentials:false}") private boolean printCredentials;
    @Value("${app.payment.job-post-fee}") private double jobPostFee;
    @Value("${app.payment.subscription-price}") private double subscriptionPrice;
    @Value("${app.payment.subscription-days}") private int subscriptionDays;

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();

    public DataSeeder(UserRepository userRepository, SkillRepository skillRepository, JobRepository jobRepository,
                      CourseRepository courseRepository, StudentProfileRepository studentProfileRepository,
                      EmployerProfileRepository employerProfileRepository, PaymentRepository paymentRepository,
                      AuthService authService, ApplicationService applicationService, SavedJobService savedJobService,
                      InterviewService interviewService, ChatService chatService,
                      JobTemplateService jobTemplateService, CacheManager cacheManager,
                      QuestionBankSeeder questionBankSeeder, FeatureDemoSeeder featureDemoSeeder,
                      Environment environment, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.jobRepository = jobRepository;
        this.courseRepository = courseRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.paymentRepository = paymentRepository;
        this.authService = authService;
        this.applicationService = applicationService;
        this.savedJobService = savedJobService;
        this.interviewService = interviewService;
        this.chatService = chatService;
        this.jobTemplateService = jobTemplateService;
        this.cacheManager = cacheManager;
        this.questionBankSeeder = questionBankSeeder;
        this.featureDemoSeeder = featureDemoSeeder;
        this.environment = environment;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        ensureAdmin(prod);
        boolean demo = ProductionRules.demoSeedingAllowed(prod, seedEnabled, allowSeedInProd);
        if (seedEnabled && !demo) {
            log.warn("SEED_ENABLED=true is IGNORED in production: no demo users, jobs or courses are created. "
                    + "(Set SEED_ALLOW_IN_PROD=true as well if you really want demo data on this deployment.)");
        }
        if (demo && prod) {
            log.warn("Demo data seeding is ENABLED in production (SEED_ALLOW_IN_PROD=true).");
        }
        if (demo && skillRepository.count() == 0) {
            log.info("Empty database: inserting demo data...");
            seedSkills();
            seedCourses();
            seedUsersJobsAndApplications();
            var cache = cacheManager.getCache("skills");
            if (cache != null) {
                cache.clear();
            }
            log.info("Demo data inserted: {} skills, {} courses, {} jobs",
                    skillRepository.count(), courseRepository.count(), jobRepository.count());
            if (printCredentials) {
                log.info("Demo logins (password {}): student1@demo.com, student2@demo.com, student3@demo.com, "
                        + "student4@demo.com, student5@demo.com, employer1@demo.com, employer2@demo.com | admin: {}",
                        DEMO_PASSWORD, adminEmail);
            }
        }
        // Independent of the one-time seed above: each runs only if its own data is missing, so
        // an existing database also gets the question banks and the newer features' demo data.
        if (seedQuestionBanks) {
            questionBankSeeder.seedIfEmpty();
        }
        if (demo) {
            featureDemoSeeder.seedIfNeeded();
        }
    }

    private void ensureAdmin(boolean prod) {
        String email = AuthService.normalizeEmail(adminEmail);
        ProductionRules.AdminDecision decision = ProductionRules.adminDecision(prod,
                !email.isEmpty() && userRepository.existsByEmailIgnoreCase(email),
                userRepository.countByRole(Role.ADMIN), email, adminPassword);
        switch (decision.action()) {
            case CREATE -> {
                authService.createUser(adminName, email, adminPassword, Role.ADMIN);
                log.info("Admin account created: {}", email);
            }
            case FAIL -> throw new ProductionConfigException(java.util.List.of(decision.reason()));
            case SKIP_ADMIN_PRESENT -> log.warn("[production config] {}", decision.reason());
            case SKIP_EXISTS, SKIP_NOT_CONFIGURED -> log.debug("Admin bootstrap: {}", decision.reason());
        }
        if (prod) {
            warnAboutDefaultAdminPasswords();
        }
    }

    /** Detection only: an admin created by an older version may still have a published default password. Never changes it. */
    private void warnAboutDefaultAdminPasswords() {
        for (User admin : userRepository.findByRole(Role.ADMIN, PageRequest.of(0, 20)).getContent()) {
            for (String known : new String[]{"Admin@123", "admin123", DEMO_PASSWORD}) {
                if (passwordEncoder.matches(known, admin.getPassword())) {
                    log.warn("[production config] The admin account {} still uses a publicly known default password. "
                            + "Log in and change it now (Settings > Change password). It was NOT changed automatically.", admin.getEmail());
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- skills

    private void seedSkills() {
        skill("Java", "Programming", "Core Java", "J2SE");
        skill("Spring Boot", "Framework", "SpringBoot", "Spring");
        skill("Python", "Programming", "Python3", "Py");
        skill("JavaScript", "Programming", "JS", "ECMAScript", "ES6");
        skill("TypeScript", "Programming", "TS");
        skill("HTML", "Web", "HTML5");
        skill("CSS", "Web", "CSS3", "Tailwind", "Bootstrap");
        skill("React", "Framework", "ReactJS", "React.js");
        skill("Node.js", "Framework", "Node", "NodeJS", "Express");
        skill("SQL", "Database", "Structured Query Language");
        skill("MySQL", "Database", "My SQL");
        skill("MongoDB", "Database", "Mongo", "Mongo DB");
        skill("Git", "Tools", "GitHub", "Version Control");
        skill("Docker", "DevOps", "Containers");
        skill("Linux", "DevOps", "Ubuntu", "Bash");
        skill("REST API", "Backend", "RESTful API", "REST");
        skill("Data Structures & Algorithms", "Computer Science", "DSA", "Algorithms");
        skill("OOP", "Computer Science", "Object Oriented Programming");
        skill("JUnit", "Testing", "Unit Testing", "JUnit5");
        skill("AWS", "Cloud", "Amazon Web Services");
        skill("Flutter", "Mobile", "Dart");
        skill("Android (Kotlin)", "Mobile", "Kotlin", "Android");
        skill("PHP", "Programming", "PHP8");
        skill("Laravel", "Framework", "Laravel PHP");
        skill("Figma", "Design", "UI Design", "UX Design");
        skill("Machine Learning", "Data", "ML");
        skill("Data Analysis", "Data", "Data Analytics", "Pandas");
        skill("Excel", "Data", "Microsoft Excel", "Spreadsheet");
        skill("Communication", "Soft Skill", "Communication Skills", "Presentation");
        skill("Problem Solving", "Soft Skill", "Critical Thinking");
        skill("Kubernetes", "DevOps", "K8s");
        skill("PostgreSQL", "Database", "Postgres");
        skill("Microservices", "Backend", "Microservice Architecture");
        skill("CI/CD", "DevOps", "Continuous Integration", "Continuous Deployment");
        skill("System Design", "Computer Science", "Software Architecture");
        skill("GraphQL", "Backend", "GQL");
    }

    private void skill(String name, String category, String... aliases) {
        Skill s = skillRepository.save(new Skill(name, category, Arrays.asList(aliases)));
        skills.put(name, s);
    }

    private String id(String skillName) {
        Skill s = skills.get(skillName);
        if (s == null) {
            throw new IllegalStateException("Seed error: unknown skill " + skillName);
        }
        return s.getId();
    }

    // ---------------------------------------------------------------- courses

    private void seedCourses() {
        course("Java Tutorials (dev.java)", "Oracle dev.java", "https://dev.java/learn/", 1, true, "Java", "OOP");
        course("Spring Boot Getting Started Guides", "Spring.io", "https://spring.io/guides", 2, true, "Spring Boot", "REST API");
        course("Spring Boot Reference Documentation", "Spring.io", "https://docs.spring.io/spring-boot/", 4, true, "Spring Boot");
        course("The Python Tutorial", "Python.org", "https://docs.python.org/3/tutorial/", 1, true, "Python");
        course("The Modern JavaScript Tutorial", "javascript.info", "https://javascript.info/", 2, true, "JavaScript");
        course("MDN Learn Web Development", "MDN", "https://developer.mozilla.org/en-US/docs/Learn", 1, true, "HTML", "CSS", "JavaScript");
        course("freeCodeCamp Responsive Web Design", "freeCodeCamp", "https://www.freecodecamp.org/learn", 1, true, "HTML", "CSS");
        course("TypeScript Handbook", "typescriptlang.org", "https://www.typescriptlang.org/docs/", 3, true, "TypeScript");
        course("Learn React (official)", "react.dev", "https://react.dev/learn", 2, true, "React");
        course("Node.js Learn", "nodejs.org", "https://nodejs.org/en/learn", 2, true, "Node.js");
        course("SQLBolt Interactive SQL Lessons", "SQLBolt", "https://sqlbolt.com/", 1, true, "SQL", "MySQL");
        course("MySQL Reference Manual", "MySQL", "https://dev.mysql.com/doc/", 4, true, "MySQL");
        course("MongoDB University", "MongoDB", "https://learn.mongodb.com/", 2, true, "MongoDB");
        course("Pro Git Book", "git-scm.com", "https://git-scm.com/book/en/v2", 1, true, "Git");
        course("Docker Get Started", "Docker Docs", "https://docs.docker.com/get-started/", 2, true, "Docker");
        course("Linux Journey", "linuxjourney.com", "https://linuxjourney.com/", 1, true, "Linux");
        course("REST API Tutorial", "restfulapi.net", "https://restfulapi.net/", 2, true, "REST API");
        course("CS50x: Introduction to Computer Science", "Harvard (edX)", "https://cs50.harvard.edu/x/", 2, true,
                "Data Structures & Algorithms", "Problem Solving");
        course("JUnit 5 User Guide", "junit.org", "https://junit.org/junit5/docs/current/user-guide/", 3, true, "JUnit");
        course("AWS Skill Builder", "AWS", "https://skillbuilder.aws/", 2, false, "AWS");
        course("Flutter Documentation", "flutter.dev", "https://docs.flutter.dev/", 2, true, "Flutter");
        course("Android Developer Courses", "Android Developers", "https://developer.android.com/courses", 2, true, "Android (Kotlin)");
        course("Laravel Documentation", "laravel.com", "https://laravel.com/docs", 3, true, "Laravel", "PHP");
        course("Kaggle Learn: Pandas & Data Visualization", "Kaggle", "https://www.kaggle.com/learn", 2, true,
                "Data Analysis", "Python", "Machine Learning");
        course("Machine Learning Crash Course", "Google", "https://developers.google.com/machine-learning/crash-course", 3, true,
                "Machine Learning");
        course("Figma Learn", "Figma", "https://help.figma.com/hc/en-us/categories/360002051613", 1, true, "Figma");
        course("Excel Help & Learning", "Microsoft", "https://support.microsoft.com/en-us/excel", 1, true, "Excel");
        course("Business Communication courses (search)", "Coursera", "https://www.coursera.org/search?query=communication", 2, false,
                "Communication");
        course("Kubernetes Documentation", "Kubernetes.io", "https://kubernetes.io/docs/home/", 3, true, "Kubernetes", "Docker");
        course("PostgreSQL Tutorial", "PostgreSQL Tutorial", "https://www.postgresqltutorial.com/", 2, true, "PostgreSQL", "SQL");
        course("Microservices.io Patterns", "microservices.io", "https://microservices.io/patterns/index.html", 4, true,
                "Microservices", "System Design");
        course("The System Design Primer", "GitHub", "https://github.com/donnemartin/system-design-primer", 4, true,
                "System Design", "Microservices");
        course("GraphQL Official Docs", "graphql.org", "https://graphql.org/learn/", 3, true, "GraphQL", "REST API");
        course("GitHub Actions: CI/CD Documentation", "GitHub Docs", "https://docs.github.com/en/actions", 3, true, "CI/CD", "Git");
    }

    private void course(String title, String platform, String url, int level, boolean free, String... skillNames) {
        Course c = new Course();
        c.setTitle(title);
        c.setPlatform(platform);
        c.setUrl(url);
        c.setLevel(level);
        c.setFree(free);
        List<String> ids = new ArrayList<>();
        for (String n : skillNames) {
            ids.add(id(n));
        }
        c.setSkillIds(ids);
        c = courseRepository.save(c);
        courses.put(title, c);
    }

    /** A CourseProgress entry pointing at one of the seeded courses, for a student's profile. */
    private CourseProgress cp(String courseTitle, CourseProgressStatus status) {
        Course c = courses.get(courseTitle);
        if (c == null) {
            throw new IllegalStateException("Seed error: unknown course " + courseTitle);
        }
        return new CourseProgress(c.getId(), c.getTitle(), status, Instant.now());
    }

    // ---------------------------------------------------------------- users, jobs, applications

    private void seedUsersJobsAndApplications() {
        User emp1 = employer("Padma Soft HR", "employer1@demo.com", "Padma Soft Ltd", "Dhaka",
                "https://example.com/padmasoft", "A fictional software company building fintech products.",
                "Padma Soft Ltd is a fictional fintech company based in Dhaka. We build payment and banking "
                        + "software for clients across South Asia. Our team values clean code, mentorship and "
                        + "steady growth over hype, and most of our engineers started out as interns.");
        User emp2 = employer("Meghna Tech HR", "employer2@demo.com", "Meghna Tech Solutions", "Chattogram",
                "https://example.com/meghnatech", "A fictional agency for web, mobile and data projects.",
                "Meghna Tech Solutions is a fictional digital agency that partners with startups on web, "
                        + "mobile and data projects. We hire for curiosity and reliability, run a four-day "
                        + "work week in summer, and love training interns into full-time teammates.");

        User s1 = student("Rahim Uddin", "student1@demo.com", "CSE", 3.62, 2027,
                "Backend enthusiast who loves Java.",
                "https://github.com/demo-rahim", "https://linkedin.com/in/demo-rahim", null,
                List.of(cp("Spring Boot Getting Started Guides", CourseProgressStatus.COMPLETED),
                        cp("Docker Get Started", CourseProgressStatus.IN_PROGRESS)),
                skEndorsed("Java", 4, "Admin"), sk("Spring Boot", 3), sk("SQL", 3),
                skEndorsed("Git", 3, "Nusrat Jahan"), sk("OOP", 4), sk("REST API", 3),
                sk("Data Structures & Algorithms", 3), sk("CI/CD", 2), sk("GraphQL", 2));
        User s2 = student("Nusrat Jahan", "student2@demo.com", "CSE", 3.45, 2026,
                "Frontend developer and UI designer.",
                "https://github.com/demo-nusrat", "https://linkedin.com/in/demo-nusrat", "https://demo-nusrat.example.com",
                List.of(cp("Learn React (official)", CourseProgressStatus.IN_PROGRESS)),
                sk("HTML", 4), skEndorsed("CSS", 4, "Admin"), sk("JavaScript", 3), sk("React", 2), sk("Figma", 3), sk("Git", 2));
        User s3 = student("Tanvir Hasan", "student3@demo.com", "EEE", 3.20, 2026,
                "Interested in data analysis.",
                null, null, null,
                List.of(),
                sk("Python", 3), sk("Data Analysis", 2), sk("Excel", 4), sk("SQL", 2), sk("Machine Learning", 1),
                sk("Communication", 4));
        User s4 = student("Imran Kabir", "student4@demo.com", "CSE", 3.80, 2027,
                "Strong backend fundamentals, looking for a Java role.",
                "https://github.com/demo-imran", null, null,
                List.of(cp("Java Tutorials (dev.java)", CourseProgressStatus.COMPLETED)),
                skEndorsed("Java", 5, "Admin"), sk("Spring Boot", 4), sk("SQL", 4), sk("Git", 3),
                sk("Data Structures & Algorithms", 4), sk("OOP", 4), sk("Docker", 3), sk("Microservices", 3),
                sk("PostgreSQL", 3), sk("Kubernetes", 2), sk("System Design", 2));
        User s5 = student("Farzana Akter", "student5@demo.com", "EEE", 2.95, 2028,
                "Just starting out, still building up my skills.",
                null, null, null,
                List.of(cp("Java Tutorials (dev.java)", CourseProgressStatus.IN_PROGRESS)),
                sk("Java", 2), sk("Git", 1));

        Job j1 = job(emp1, "Junior Java Backend Developer", JobType.FULL_TIME, "Dhaka", 30,
                "Build REST APIs for our payment platform using Java and Spring Boot.",
                req("Java", 4, 5, true), req("Spring Boot", 3, 5, true), req("SQL", 3, 3, false),
                req("Git", 2, 2, false), req("Docker", 2, 2, false));
        job(emp1, "Spring Boot Intern", JobType.INTERNSHIP, "Dhaka", 20,
                "Learn backend development with Spring Boot and MongoDB in a real team.",
                req("Java", 3, 4, true), req("Spring Boot", 2, 3, false), req("MongoDB", 2, 3, false),
                req("Git", 1, 2, false));
        job(emp1, "QA Engineer (Automation)", JobType.FULL_TIME, "Dhaka", 25,
                "Write automated tests for backend services.",
                req("Java", 3, 3, true), req("JUnit", 3, 5, true), req("REST API", 2, 3, false),
                req("Problem Solving", 3, 2, false));
        Job jDevops = job(emp1, "DevOps Trainee", JobType.INTERNSHIP, "Remote", 18,
                "Help us automate builds and deployments.",
                req("Linux", 2, 4, true), req("Docker", 2, 5, true), req("Git", 2, 3, false), req("AWS", 1, 3, false));
        job(emp1, "Full Stack Developer (Java + React)", JobType.FULL_TIME, "Dhaka", 40,
                "Work across our Spring Boot backend and React frontend.",
                req("Java", 3, 4, true), req("Spring Boot", 3, 4, false), req("React", 3, 4, true),
                req("JavaScript", 3, 3, false), req("SQL", 2, 2, false));
        job(emp1, "Data Structures Tutor (Part-time)", JobType.PART_TIME, "Dhaka", 15,
                "Teach DSA to first-year students in the evening.",
                req("Data Structures & Algorithms", 4, 5, true), req("Communication", 3, 4, true),
                req("Java", 3, 2, false));
        job(emp1, "Android Developer", JobType.FULL_TIME, "Dhaka", 35,
                "Build our mobile wallet app in Kotlin.",
                req("Android (Kotlin)", 3, 5, true), req("REST API", 2, 3, false), req("Git", 2, 2, false));
        job(emp1, "Cloud Support Associate", JobType.FULL_TIME, "Sylhet", 28,
                "Support customers using cloud infrastructure.",
                req("AWS", 2, 5, true), req("Linux", 2, 4, false), req("Communication", 3, 3, false));
        Job jUrgent = job(emp1, "Backend Support Engineer (Urgent)", JobType.FULL_TIME, "Dhaka", 2,
                "Help us fix production issues on our Java backend. Fast turnaround role, deadline is very near.",
                req("Java", 3, 4, true), req("SQL", 2, 3, false), req("Git", 2, 2, false));
        job(emp1, "Senior Backend Engineer (Microservices)", JobType.FULL_TIME, "Dhaka", 35,
                "Help split our monolith into Spring Boot microservices and own the PostgreSQL schema for one of them.",
                req("Java", 4, 5, true), req("Spring Boot", 4, 5, true), req("Microservices", 3, 5, true),
                req("Docker", 2, 3, false), req("PostgreSQL", 2, 3, false));
        job(emp1, "DevOps Specialist", JobType.FULL_TIME, "Dhaka", 30,
                "Own our CI/CD pipelines and container orchestration as we move more services to Kubernetes.",
                req("Docker", 3, 5, true), req("Kubernetes", 2, 5, true), req("CI/CD", 2, 4, true),
                req("Linux", 3, 3, false), req("AWS", 2, 3, false));
        job(emp1, "Cloud Solutions Architect", JobType.FULL_TIME, "Dhaka", 33,
                "Design the cloud infrastructure and high-level architecture for new fintech products.",
                req("AWS", 3, 5, true), req("System Design", 3, 5, true), req("Docker", 2, 3, false),
                req("Linux", 2, 3, false));

        Job j9 = job(emp2, "Frontend Developer (React)", JobType.FULL_TIME, "Chattogram", 30,
                "Build responsive web apps with React.",
                req("HTML", 3, 3, false), req("CSS", 3, 3, false), req("JavaScript", 3, 5, true),
                req("React", 3, 5, true), req("Git", 2, 2, false));
        job(emp2, "UI/UX Design Intern", JobType.INTERNSHIP, "Remote", 21,
                "Design screens and prototypes for client projects.",
                req("Figma", 3, 5, true), req("HTML", 2, 2, false), req("CSS", 2, 2, false),
                req("Communication", 3, 3, false));
        job(emp2, "Junior Data Analyst", JobType.FULL_TIME, "Dhaka", 26,
                "Clean data and build reports for clients.",
                req("Excel", 3, 3, false), req("SQL", 3, 5, true), req("Python", 2, 4, false),
                req("Data Analysis", 3, 5, true), req("Communication", 2, 2, false));
        Job jMl = job(emp2, "Machine Learning Intern", JobType.INTERNSHIP, "Remote", 22,
                "Train and evaluate ML models for a research project.",
                req("Python", 3, 5, true), req("Machine Learning", 2, 5, true), req("Data Analysis", 2, 3, false));
        job(emp2, "PHP Laravel Developer", JobType.FULL_TIME, "Chattogram", 32,
                "Maintain e-commerce sites built with Laravel.",
                req("PHP", 3, 5, true), req("Laravel", 3, 5, true), req("MySQL", 3, 3, false), req("Git", 2, 2, false));
        job(emp2, "Flutter Mobile Developer", JobType.FULL_TIME, "Remote", 27,
                "Build cross-platform apps with Flutter.",
                req("Flutter", 3, 5, true), req("REST API", 2, 3, false), req("Git", 2, 2, false));
        job(emp2, "Node.js Backend Intern", JobType.INTERNSHIP, "Chattogram", 19,
                "Build APIs with Node.js and MongoDB.",
                req("JavaScript", 3, 4, true), req("Node.js", 2, 4, false), req("MongoDB", 2, 3, false),
                req("REST API", 2, 3, false));
        job(emp2, "UI/UX Product Designer", JobType.FULL_TIME, "Remote", 24,
                "Own the end-to-end design process for client products, from wireframes to a polished UI.",
                req("Figma", 4, 5, true), req("Communication", 3, 4, true), req("HTML", 2, 2, false),
                req("CSS", 2, 2, false));
        job(emp2, "AI/ML Engineer", JobType.FULL_TIME, "Remote", 29,
                "Build and ship machine learning models into production client projects, not just notebooks.",
                req("Python", 4, 5, true), req("Machine Learning", 3, 5, true), req("Data Analysis", 2, 3, false),
                req("SQL", 2, 2, false));
        job(emp2, "Backend Developer (GraphQL)", JobType.FULL_TIME, "Chattogram", 26,
                "Build a GraphQL API layer in front of our existing Node.js services.",
                req("Node.js", 3, 4, true), req("GraphQL", 2, 5, true), req("JavaScript", 3, 4, false),
                req("MongoDB", 2, 2, false));

        // ---- Applications: a mix of statuses so dashboards, filtering and bulk actions have real data.
        ApplicationResponse a1 = applicationService.apply(auth(s1), j1.getId(), true);
        ApplicationResponse a2 = applicationService.apply(auth(s2), j9.getId(), true);
        ApplicationResponse a3 = applicationService.apply(auth(s3), j9.getId(), true);
        ApplicationResponse a4 = applicationService.apply(auth(s4), j1.getId(), true);
        ApplicationResponse a5 = applicationService.apply(auth(s5), j1.getId(), true);

        // Move applications through the pipeline: APPLIED -> SHORTLISTED -> HIRED/REJECTED.
        // a4 (Imran, strong match) is left APPLIED on purpose, so the employer dashboard has
        // something ready for a bulk shortlist/reject action.
        applicationService.updateStatus(auth(emp1), a1.id(), ApplicationStatus.SHORTLISTED);
        applicationService.updateStatus(auth(emp1), a5.id(), ApplicationStatus.REJECTED);
        applicationService.updateStatus(auth(emp2), a2.id(), ApplicationStatus.SHORTLISTED);
        applicationService.updateStatus(auth(emp2), a2.id(), ApplicationStatus.HIRED);
        applicationService.updateStatus(auth(emp2), a3.id(), ApplicationStatus.SHORTLISTED);

        // ---- In-app chat: only opens once an application is shortlisted/hired.
        chatService.send(auth(emp1), a1.id(),
                "Hi Rahim, thanks for applying! Could you share your availability for a quick call this week?");
        chatService.send(auth(s1), a1.id(),
                "Hi, thanks for reaching out! I'm free tomorrow afternoon or Thursday morning.");
        chatService.send(auth(emp2), a2.id(),
                "Congratulations Nusrat, welcome to the team! HR will be in touch about onboarding this week.");
        chatService.send(auth(s2), a2.id(), "Thank you so much, I'm really looking forward to it!");

        // ---- Interviews: one already scheduled (student picked a slot), one still awaiting a pick.
        InterviewResponse iv1 = interviewService.propose(auth(emp1), a1.id(),
                List.of("2026-10-14T11:00:00+06:00", "2026-10-15T11:00:00+06:00"));
        interviewService.selectSlot(auth(s1), iv1.id(), "2026-10-14T11:00:00+06:00");
        interviewService.propose(auth(emp2), a3.id(),
                List.of("2026-10-15T10:00:00+06:00", "2026-10-16T15:00:00+06:00"));

        // ---- Bookmarked/saved jobs (before applying). Rahim's bookmark has a deadline only 2 days
        // away, so the deadline-reminder scheduler has something to fire on right at startup.
        savedJobService.save(s1.getId(), jUrgent.getId());
        savedJobService.save(s2.getId(), jDevops.getId());
        savedJobService.save(s3.getId(), jMl.getId());

        // ---- Reusable job templates (save a skill set for future postings).
        jobTemplateService.create(emp1.getId(), new JobTemplateRequest(
                "Standard Java Backend Role", "Java Backend Developer",
                "Build and maintain REST APIs with Java and Spring Boot.", JobType.FULL_TIME, "Dhaka",
                List.of(new RequiredSkillRequest(id("Java"), 4, 5, true),
                        new RequiredSkillRequest(id("Spring Boot"), 3, 4, true),
                        new RequiredSkillRequest(id("SQL"), 2, 3, false))));
        jobTemplateService.create(emp2.getId(), new JobTemplateRequest(
                "Standard Frontend Role", "Frontend Developer",
                "Build responsive UIs with React.", JobType.FULL_TIME, "Chattogram",
                List.of(new RequiredSkillRequest(id("HTML"), 3, 3, false),
                        new RequiredSkillRequest(id("CSS"), 3, 3, false),
                        new RequiredSkillRequest(id("JavaScript"), 3, 5, true),
                        new RequiredSkillRequest(id("React"), 3, 5, true))));

        // ---- Payments module demo data: emp2 already has an active Pro subscription (so none of
        // its seeded jobs above needed a posting fee), and emp1 has one completed job-posting
        // payment on record, so the billing-history view has example content out of the box.
        // jobsPostedCount is snapshotted from the jobs just seeded above (not hardcoded), so posting
        // one more job as emp1 in a demo correctly needs payment right away, same as it would for any
        // real employer who already has this many lifetime posts.
        employerProfileRepository.findByUserId(emp1.getId()).ifPresent(profile -> {
            profile.setJobsPostedCount((int) jobRepository.countByEmployerId(emp1.getId()));
            employerProfileRepository.save(profile);
        });
        employerProfileRepository.findByUserId(emp2.getId()).ifPresent(profile -> {
            profile.setJobsPostedCount((int) jobRepository.countByEmployerId(emp2.getId()));
            profile.setSubscriptionPlan(SubscriptionPlan.PRO);
            profile.setSubscriptionExpiresAt(Instant.now().plus(subscriptionDays, ChronoUnit.DAYS));
            employerProfileRepository.save(profile);
        });

        Payment demoPayment = new Payment();
        demoPayment.setEmployerId(emp1.getId());
        demoPayment.setTranId("JOB-DEMO-" + emp1.getId());
        demoPayment.setPurpose(PaymentPurpose.JOB_POST);
        demoPayment.setJobId(j1.getId());
        demoPayment.setAmount(jobPostFee);
        demoPayment.setStatus(PaymentStatus.SUCCESS);
        demoPayment.setPaymentMethod("bKash");
        demoPayment.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        demoPayment.setPaidAt(Instant.now().minus(10, ChronoUnit.DAYS));
        paymentRepository.save(demoPayment);
    }

    private User employer(String name, String email, String company, String location, String website, String desc,
                          String aboutUs) {
        User u = authService.createUser(name, email, DEMO_PASSWORD, Role.EMPLOYER);
        EmployerProfile p = new EmployerProfile();
        p.setUserId(u.getId());
        p.setCompanyName(company);
        p.setLocation(location);
        p.setWebsite(website);
        p.setDescription(desc);
        p.setAboutUs(aboutUs);
        // No logo file is seeded here (there is no real image to store); uploading one is a
        // manual step via the employer profile page, same as a CV upload is for a student.
        employerProfileRepository.save(p);
        return u;
    }

    private User student(String name, String email, String dept, double cgpa, int gradYear, String bio,
                         String githubUrl, String linkedinUrl, String websiteUrl, List<CourseProgress> courseProgress,
                         StudentSkill... studentSkills) {
        User u = authService.createUser(name, email, DEMO_PASSWORD, Role.STUDENT);
        StudentProfile p = new StudentProfile();
        p.setUserId(u.getId());
        p.setFullName(name);
        p.setDepartment(dept);
        p.setCgpa(cgpa);
        p.setGraduationYear(gradYear);
        p.setBio(bio);
        p.setGithubUrl(githubUrl);
        p.setLinkedinUrl(linkedinUrl);
        p.setWebsiteUrl(websiteUrl);
        p.setCourseProgress(new ArrayList<>(courseProgress));
        p.setSkills(new ArrayList<>(Arrays.asList(studentSkills)));
        studentProfileRepository.save(p);
        return u;
    }

    private StudentSkill sk(String skillName, int level) {
        return new StudentSkill(id(skillName), skillName, level);
    }

    /** A skill already endorsed by an admin or a peer (boolean badge, like LinkedIn). */
    private StudentSkill skEndorsed(String skillName, int level, String endorsedByName) {
        StudentSkill s = sk(skillName, level);
        s.setEndorsed(true);
        s.setEndorsedByName(endorsedByName);
        s.setEndorsedAt(Instant.now());
        return s;
    }

    private RequiredSkill req(String skillName, int level, int weight, boolean mandatory) {
        return new RequiredSkill(id(skillName), skillName, level, weight, mandatory);
    }

    private Job job(User employer, String title, JobType type, String location, int daysUntilDeadline,
                    String description, RequiredSkill... required) {
        String company = employerProfileRepository.findByUserId(employer.getId())
                .map(EmployerProfile::getCompanyName).orElse(employer.getName());
        Job j = new Job();
        j.setEmployerId(employer.getId());
        j.setCompanyName(company);
        j.setTitle(title);
        j.setType(type);
        j.setLocation(location);
        j.setDescription(description);
        j.setDeadline(JobService.today().plusDays(daysUntilDeadline));
        j.setStatus(JobStatus.OPEN);
        j.setRequiredSkills(new ArrayList<>(Arrays.asList(required)));
        // Spread creation dates over the last months so the admin chart has data.
        j.setCreatedAt(Instant.now().minus(daysUntilDeadline * 3L, ChronoUnit.DAYS));
        return jobRepository.save(j);
    }

    private static AuthUser auth(User u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getName(), u.getRole());
    }
}
