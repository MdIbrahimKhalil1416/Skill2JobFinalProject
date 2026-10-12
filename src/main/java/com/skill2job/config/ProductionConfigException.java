package com.skill2job.config;

import java.util.List;

/** Thrown at startup when the production profile is active with an unsafe configuration. */
public class ProductionConfigException extends RuntimeException {

    private final List<String> problems;

    public ProductionConfigException(List<String> problems) {
        super("Unsafe production configuration: " + String.join(" | ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() { return problems; }
}
