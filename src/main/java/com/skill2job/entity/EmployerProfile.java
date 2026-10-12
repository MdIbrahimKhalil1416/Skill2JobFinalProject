package com.skill2job.entity;

import com.skill2job.enums.SubscriptionPlan;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "employer_profiles")
public class EmployerProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String companyName;
    private String website;
    private String description;
    private String location;

    /** Longer "About us" text shown on the public company/job page (company branding). */
    private String aboutUs;
    /** The generated, collision-free name the logo is stored under on disk (never the original name). */
    private String logoStoredFilename;
    private String logoOriginalFilename;

    /** Payment module: FREE until a successful SUBSCRIPTION payment sets this to PRO (see PaymentService). */
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;
    /** Null while on FREE; while on PRO, the plan is active only until this instant (extended by each
     * renewal payment rather than overwritten, so paying early never loses remaining time). */
    private Instant subscriptionExpiresAt;
    /** Tran ids of the subscription payments already added to subscriptionExpiresAt. Written in the SAME atomic
     * update as the extension, which is what makes "extend the plan for payment X" happen at most once. */
    private List<String> appliedPaymentIds = new ArrayList<>();
    /** Lifetime count of jobs this employer has ever posted, incremented once in JobService.create()
     * and never decremented - deliberately NOT derived from a live count of their current jobs, so
     * deleting a job can never refund a used free-post slot (PaymentService.jobPostingRequiresPayment
     * reads this, not JobRepository.countByEmployerId). */
    private int jobsPostedCount = 0;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getAboutUs() { return aboutUs; }
    public void setAboutUs(String aboutUs) { this.aboutUs = aboutUs; }
    public String getLogoStoredFilename() { return logoStoredFilename; }
    public void setLogoStoredFilename(String logoStoredFilename) { this.logoStoredFilename = logoStoredFilename; }
    public String getLogoOriginalFilename() { return logoOriginalFilename; }
    public void setLogoOriginalFilename(String logoOriginalFilename) { this.logoOriginalFilename = logoOriginalFilename; }
    public boolean hasLogo() { return logoStoredFilename != null && !logoStoredFilename.isBlank(); }
    public SubscriptionPlan getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }
    public Instant getSubscriptionExpiresAt() { return subscriptionExpiresAt; }
    public void setSubscriptionExpiresAt(Instant subscriptionExpiresAt) { this.subscriptionExpiresAt = subscriptionExpiresAt; }
    public List<String> getAppliedPaymentIds() { return appliedPaymentIds; }
    public void setAppliedPaymentIds(List<String> appliedPaymentIds) { this.appliedPaymentIds = appliedPaymentIds == null ? new ArrayList<>() : appliedPaymentIds; }
    public int getJobsPostedCount() { return jobsPostedCount; }
    public void setJobsPostedCount(int jobsPostedCount) { this.jobsPostedCount = jobsPostedCount; }
    /** True while on PRO and not yet expired - the one check that decides whether a job post is free. */
    public boolean isProActive() {
        return subscriptionPlan == SubscriptionPlan.PRO
                && subscriptionExpiresAt != null && subscriptionExpiresAt.isAfter(Instant.now());
    }
}
