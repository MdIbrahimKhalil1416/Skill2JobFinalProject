package com.skill2job.cvbuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CvBuilderDtos {

    private CvBuilderDtos() { }

    private static final String LINK = "^(|https?://.*)$";

    public record Project(@NotBlank(message = "Every project needs a title") @Size(max = 100) String title,
                          @Size(max = 150) String techStack,
                          @Size(max = 700) String description,
                          @Pattern(regexp = LINK, message = "Project links must start with http:// or https://") @Size(max = 300) String link) { }

    public record Experience(@NotBlank(message = "Every experience needs a title") @Size(max = 100) String title,
                             @Size(max = 100) String organization,
                             @Size(max = 30) String startDate,
                             @Size(max = 30) String endDate,
                             @Size(max = 700) String description) { }

    public record Education(@NotBlank(message = "Every education entry needs a degree") @Size(max = 120) String degree,
                            @Size(max = 120) String institution,
                            @Size(max = 10) String startYear,
                            @Size(max = 10) String endYear,
                            @Size(max = 60) String result) { }

    public record Certification(@NotBlank(message = "Every certificate needs a name") @Size(max = 120) String name,
                                @Size(max = 100) String issuer,
                                @Size(max = 10) String year,
                                @Pattern(regexp = LINK, message = "Certificate links must start with http:// or https://") @Size(max = 300) String link) { }

    public record CvRequest(@Size(max = 120) String headline,
                            @Pattern(regexp = "^[0-9+()\\- ]{0,30}$", message = "Phone can contain digits, spaces, + - ( ) only") String phone,
                            @Size(max = 80) String city,
                            @Size(max = 8, message = "At most 8 projects") List<@Valid Project> projects,
                            @Size(max = 8, message = "At most 8 experience entries") List<@Valid Experience> experiences,
                            @Size(max = 5, message = "At most 5 education entries") List<@Valid Education> education,
                            @Size(max = 10, message = "At most 10 certificates") List<@Valid Certification> certifications,
                            @Size(max = 8, message = "At most 8 languages") List<@NotBlank @Size(max = 30) String> languages,
                            @Pattern(regexp = "^(|modern|classic|minimal)$", message = "Unknown template") String template) { }

    public record SkillLine(String name, int level, boolean verified, Integer verifiedLevel, boolean endorsed) { }

    /** Everything the CV preview needs, merged from the profile, the account and the CV sections. */
    public record CvResponse(String fullName, String email, String headline, String phone, String city,
                             String department, Double cgpa, Integer graduationYear, String bio,
                             String githubUrl, String linkedinUrl, String websiteUrl,
                             List<SkillLine> skills, List<String> completedCourses,
                             List<Project> projects, List<Experience> experiences, List<Education> education,
                             List<Certification> certifications, List<String> languages, String template,
                             boolean hasUploadedCv) { }
}
