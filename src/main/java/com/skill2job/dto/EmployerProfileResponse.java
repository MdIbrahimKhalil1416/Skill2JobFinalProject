package com.skill2job.dto;

import com.skill2job.entity.EmployerProfile;
import com.skill2job.enums.SubscriptionPlan;

import java.time.Instant;

public record EmployerProfileResponse(String id, String userId, String companyName, String website,
                                      String description, String location, String aboutUs, boolean hasLogo,
                                      SubscriptionPlan subscriptionPlan, Instant subscriptionExpiresAt, boolean proActive) {
    public static EmployerProfileResponse from(EmployerProfile p) {
        return new EmployerProfileResponse(p.getId(), p.getUserId(), p.getCompanyName(), p.getWebsite(),
                p.getDescription(), p.getLocation(), p.getAboutUs(), p.hasLogo(),
                p.getSubscriptionPlan(), p.getSubscriptionExpiresAt(), p.isProActive());
    }
}
