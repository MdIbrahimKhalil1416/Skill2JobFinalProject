package com.skill2job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of Skill2Job.
 * EnableScheduling -> runs the daily job-closing task.
 * EnableCaching    -> caches the skill list in memory.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class Skill2JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(Skill2JobApplication.class, args);
    }
}
