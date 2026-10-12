package com.skill2job.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One audited action: who did what to which record, when, and whether it succeeded. */
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    /** TTL index: entries older than one year are removed by MongoDB automatically. */
    @Indexed(expireAfter = "365d")
    private Instant at;

    @Indexed
    private String actorId;
    private String actorEmail;
    /** STUDENT / EMPLOYER / ADMIN, or SYSTEM for schedulers, the startup seeder and gateway callbacks. */
    private String actorRole;

    @Indexed
    private String action;
    private String targetType;
    private String targetId;
    private String details;

    private boolean success;
    private String errorMessage;
    private String ipAddress;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
