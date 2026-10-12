package com.skill2job.alerts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobAlertLimitTest {

    @Test
    void studentProRaisesTheAlertCap() {
        assertEquals(10, JobAlertService.maxAlerts(false));
        assertEquals(30, JobAlertService.maxAlerts(true));
        assertTrue(JobAlertService.maxAlerts(true) > JobAlertService.maxAlerts(false));
    }
}
