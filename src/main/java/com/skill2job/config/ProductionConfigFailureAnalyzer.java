package com.skill2job.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Shows the list of unsafe settings instead of a stack trace. Registered in META-INF/spring.factories. */
public class ProductionConfigFailureAnalyzer extends AbstractFailureAnalyzer<ProductionConfigException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ProductionConfigException cause) {
        StringBuilder description = new StringBuilder("Skill2Job refused to start with the production profile "
                + "because its configuration is not safe:\n");
        for (String problem : cause.getProblems()) {
            description.append("  - ").append(problem).append('\n');
        }
        String action = "Fix the Environment Variables listed above (on Render: your service > Environment) and redeploy. "
                + "See DEPLOY.md, section \"Environment variables\". For local development use the default dev profile "
                + "(do not set SPRING_PROFILES_ACTIVE=prod).";
        return new FailureAnalysis(description.toString(), action, cause);
    }
}
