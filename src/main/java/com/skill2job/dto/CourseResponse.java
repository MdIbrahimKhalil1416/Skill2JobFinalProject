package com.skill2job.dto;

import java.util.List;

public record CourseResponse(String id, String title, String platform, String url, int level, boolean free,
                             List<String> skillIds, List<String> skillNames) {
}
