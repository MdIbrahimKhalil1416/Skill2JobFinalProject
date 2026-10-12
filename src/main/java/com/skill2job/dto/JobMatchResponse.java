package com.skill2job.dto;

/** A job plus how well the current student matches it. */
public record JobMatchResponse(JobResponse job, double matchPercent, boolean eligible, int missingCount) {
}
