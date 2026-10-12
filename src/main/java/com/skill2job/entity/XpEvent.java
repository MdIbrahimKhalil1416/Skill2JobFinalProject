package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One XP award, kept for the "recent activity" list on the achievements page. */
@Document(collection = "xp_events")
public class XpEvent {

    @Id
    private String id;
    @Indexed
    private String studentId;
    private int xp;
    private String reason;
    private Instant at;

    public XpEvent() { }

    public XpEvent(String studentId, int xp, String reason, Instant at) {
        this.studentId = studentId;
        this.xp = xp;
        this.reason = reason;
        this.at = at;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
