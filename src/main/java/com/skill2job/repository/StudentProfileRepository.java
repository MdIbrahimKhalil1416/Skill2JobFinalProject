package com.skill2job.repository;

import com.skill2job.entity.StudentProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends MongoRepository<StudentProfile, String> {
    Optional<StudentProfile> findByUserId(String userId);

    @Query(value = "{ 'skills.skillId': ?0 }", exists = true)
    boolean existsBySkillId(String skillId);

    /** Leaderboard: students with the most XP first. */
    List<StudentProfile> findTop20ByXpGreaterThanOrderByXpDesc(int minXp);

    boolean existsByCvStoredFilename(String cvStoredFilename);

    List<StudentProfile> findByUserIdIn(Collection<String> userIds);
}
