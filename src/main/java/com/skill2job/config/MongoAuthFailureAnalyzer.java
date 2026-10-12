package com.skill2job.config;

import com.mongodb.MongoSecurityException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Clear message when the MongoDB username or password in MONGODB_URI is wrong. */
public class MongoAuthFailureAnalyzer extends AbstractFailureAnalyzer<MongoSecurityException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MongoSecurityException cause) {
        return new FailureAnalysis(
                "MongoDB rejected the username or password in MONGODB_URI.",
                "Check the database user in MongoDB Atlas (Database Access). If the password contains special "
                        + "characters such as @ : / ? #, reset it to letters and digits only, then update MONGODB_URI.",
                cause);
    }
}
