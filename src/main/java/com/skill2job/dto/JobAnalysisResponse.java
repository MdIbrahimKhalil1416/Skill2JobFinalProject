package com.skill2job.dto;

import com.skill2job.matching.MatchResult;

import java.util.List;

/** Everything the job-detail page needs: match, gap report and course suggestions. */
public record JobAnalysisResponse(
        JobResponse job,
        MatchResult match,
        List<CourseSuggestion> suggestions,
        boolean alreadyApplied,
        String applicationStatus
) {
}
