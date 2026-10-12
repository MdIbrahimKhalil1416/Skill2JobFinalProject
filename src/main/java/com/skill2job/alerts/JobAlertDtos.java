package com.skill2job.alerts;

import com.skill2job.enums.JobType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class JobAlertDtos {

    private JobAlertDtos() { }

    public record AlertRequest(
            @NotBlank(message = "Give the alert a name") @Size(max = 60, message = "Name is too long") String name,
            @Size(max = 80) String keyword,
            JobType type,
            @Size(max = 80) String location,
            @Size(max = 40) String skillId,
            @Min(value = 0, message = "Minimum match must be 0-100") @Max(value = 100, message = "Minimum match must be 0-100") Integer minMatch,
            boolean emailEnabled,
            Boolean active) { }

    public record AlertResponse(String id, String name, String keyword, JobType type, String location, String skillId,
                                String skillName, Integer minMatch, boolean emailEnabled, boolean active,
                                Instant createdAt, Instant lastNotifiedAt, int totalJobsSent) { }

    public record RunResult(int alertsChecked, int newJobs) { }
}
