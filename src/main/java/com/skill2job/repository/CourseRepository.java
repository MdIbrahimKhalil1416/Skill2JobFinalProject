package com.skill2job.repository;

import com.skill2job.entity.Course;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CourseRepository extends MongoRepository<Course, String> {

    /** Courses whose skillIds array contains the given skill id. */
    @Query("{ 'skillIds': ?0 }")
    List<Course> findBySkillId(String skillId);
}
