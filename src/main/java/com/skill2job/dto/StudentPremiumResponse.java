package com.skill2job.dto;

import java.time.Instant;

/** What the student checkout page shows: the Student Pro price and duration, and whether it is active now. */
public record StudentPremiumResponse(boolean active, Instant expiresAt, double price, String currency, int days) {
}
