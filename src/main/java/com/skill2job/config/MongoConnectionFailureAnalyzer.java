package com.skill2job.config;

import com.mongodb.MongoTimeoutException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Replaces the long stack trace with a clear message when MongoDB cannot be reached at startup.
 * Registered in META-INF/spring.factories.
 *
 * The driver's own message is kept (shortened): it names the host(s) it tried and why each failed
 * (e.g. "localhost:27017 ... Connection refused" = MONGODB_URI is not set; "MongoSecurityException" =
 * wrong username/password; a timeout on *.mongodb.net = Atlas Network Access blocks this server).
 * That message contains host names and error text, never the password.
 */
public class MongoConnectionFailureAnalyzer extends AbstractFailureAnalyzer<MongoTimeoutException> {

    private static final int MAX_DETAIL = 1500;

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MongoTimeoutException cause) {
        String detail = cause.getMessage() == null ? "(no detail)" : cause.getMessage();
        if (detail.length() > MAX_DETAIL) {
            detail = detail.substring(0, MAX_DETAIL) + " ...";
        }
        String description = "Skill2Job could not connect to MongoDB. "
                + "The application needs a running MongoDB server to start.\n\n"
                + "Driver detail: " + detail;
        String action = """
                Read the "Driver detail" above:
                  - It mentions localhost:27017  -> MONGODB_URI is not set. On Render: Environment > add MONGODB_URI
                    (exact name) with your Atlas connection string. Locally: put it in a .env file in the project root.
                  - It mentions MongoSecurityException / "Exception authenticating" -> the username or password in the
                    URI is wrong. Atlas > Database Access: reset the user's password (letters and digits only).
                  - It mentions *.mongodb.net with a timeout / no exception -> Atlas blocks this server.
                    Atlas > Network Access: add 0.0.0.0/0 (Render's free plan has no fixed IP).
                  - It mentions UnknownHost / DNS -> the cluster address in the URI is mistyped; copy it again from
                    Atlas > Connect > Drivers.
                Local alternatives: install MongoDB Community Server (mongodb://localhost:27017), or run
                docker compose up --build. See README.md / DEPLOY.md.""";
        return new FailureAnalysis(description, action, cause);
    }
}
