package com.skill2job.reviews;

import com.skill2job.entity.CompanyReview;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyReviewServiceTest {

    private static CompanyReview stars(int n) {
        CompanyReview r = new CompanyReview();
        r.setRating(n);
        return r;
    }

    @Test
    void averageAndDistribution() {
        CompanyReviewService.RatingSummary s = CompanyReviewService.summarize(List.of(stars(5), stars(4), stars(4), stars(1)));
        assertEquals(3.5, s.average(), 0.001);
        assertEquals(4, s.count());
        assertEquals(List.of(1L, 0L, 0L, 2L, 1L), s.distribution());
    }

    @Test
    void noReviewsGivesZero() {
        CompanyReviewService.RatingSummary s = CompanyReviewService.summarize(List.of());
        assertEquals(0.0, s.average(), 0.001);
        assertEquals(0, s.count());
    }

    @Test
    void publicNamesAreShortened() {
        assertEquals("Nusrat J.", CompanyReviewService.shortName("Nusrat Jahan"));
        assertEquals("Imran", CompanyReviewService.shortName("Imran"));
    }
}
