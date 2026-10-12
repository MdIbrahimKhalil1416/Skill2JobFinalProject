package com.skill2job.analytics;

import com.skill2job.dto.CountItem;
import com.skill2job.entity.JobApplication;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployerAnalyticsServiceTest {

    private static JobApplication app(double match) {
        JobApplication a = new JobApplication();
        a.setMatchPercent(match);
        return a;
    }

    @Test
    void matchBucketsIncludeHundredInTheTopBucket() {
        List<CountItem> d = EmployerAnalyticsService.matchDistribution(List.of(app(0), app(19.9), app(20), app(59.9), app(80), app(100)));
        assertEquals(List.of(2L, 1L, 1L, 0L, 2L), d.stream().map(CountItem::count).toList());
    }

    @Test
    void percentagesRoundToOneDecimalAndHandleZero() {
        assertEquals(33.3, EmployerAnalyticsService.pct(1, 3), 0.001);
        assertEquals(0.0, EmployerAnalyticsService.pct(5, 0), 0.001);
    }
}
